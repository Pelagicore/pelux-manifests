#!/usr/bin/groovy

// Copyright (C) Luxoft Sweden AB 2018

// Helper to do checkout and update of submodules.
void gitSubmoduleUpdate() {
    sh "git submodule update --init"
}

void repoInit(String manifest, String yoctoDir) {
    syncDir = "/workspace"
    sh "/workspace/ci-scripts/do_repo_init ${manifest} ${syncDir} ${yoctoDir}"
}

void setupBitbake(String yoctoDir, String templateConf, boolean doArchiveCache, boolean smokeTests, boolean analyzeImage) {
    sh "/workspace/cookbook/yocto/initialize-bitbake.sh ${yoctoDir} ${templateConf}"

    // Add other settings that are CI specific to the local.conf
    sh "cat /workspace/conf/local.conf.appendix >> ${yoctoDir}/build/conf/local.conf"

    // Uncomment `ACCEPT_FSL_EULA = "1"` to accept the Freescale EULA
    // in local.conf if ACCEPT_FSL_EULA parameter is set to true
    if (getBoolEnvVar("ACCEPT_FSL_EULA", false)) {
        sh "sed -i '/ACCEPT_FSL_EULA/s/^#//g' ${yoctoDir}/build/conf/local.conf"
    }

    // Add settings for smoke testing if needed
    if (smokeTests) {
        sh "echo '' >> ${yoctoDir}/build/conf/local.conf"
        sh "cat /workspace/conf/test-scripts/local.conf.appendix >> ${yoctoDir}/build/conf/local.conf"
    }

    if (doArchiveCache){
        //Mirror git repositories
        sh "cat /workspace/conf/local.conf_mirror.appendix >> ${yoctoDir}/build/conf/local.conf"
    }

    if (analyzeImage){
        //Set up the configuration for running ISAFW
        sh "cat /workspace/conf/isafw.local.conf >> ${yoctoDir}/build/conf/local.conf"
        sh "cat /workspace/conf/isafw.bblayers.conf >> ${yoctoDir}/build/conf/bblayers.conf"
    }
}

void setupCache(String yoctoDir, String url) {
    // Setup site.conf if not building the master to do a incremental build.
    // The YOCTO_CACHE_URL can be set for a Jenkins job as a parameter.
    if (url?.trim()) {
        sh "sed 's|%CACHEURL%|${url}|g' /workspace/site.conf.in > ${yoctoDir}/build/conf/site.conf"
        echo "Cache set up"
    } else {
        echo "No cache setup"
    }
}

void buildImage(String yoctoDir, String imageName, String variantName) {
    // If we have a site.conf, that means we have caching, if we don't that
    // means we should do a fetchall.
    def statusCode = sh script:"test -f ${yoctoDir}/build/conf/site.conf", returnStatus:true
    if (statusCode != 0) {
        echo "Fetching sources for recipes"
        sh "/workspace/cookbook/yocto/fetch-sources-for-recipes.sh ${yoctoDir} ${imageName}"
    } else {
        echo "\'site.conf\' exists, using cache"
    }
    sh "/workspace/cookbook/yocto/build-images.sh ${yoctoDir} ${imageName}"
}

// In order to run smoke tests, the -dev image should be specified because of the dependencies
void runSmokeTests(String yoctoDir, String imageName) {
    String archiveDir = "testReports-" + imageName

    try {
        sh "/workspace/cookbook/yocto/runqemu-smoke-test.sh ${yoctoDir} ${imageName}"
    } catch(e) {
        echo "There were failing tests"
        println(e.getMessage())
    } finally {
        reportsDir="/workspace/${archiveDir}/test_reports/${imageName}/"
        sh "mkdir -p ${reportsDir}"
        if (fileExists("pelux_yocto/build/tmp/log/oeqa/testresults.json")) {
            // Since `thud`, poky test report consists of a single JSON
            // file; we need to convert it into jUnit format.
            sh "mkdir -p ${yoctoDir}/build/TestResults/"
            sh "cd ${yoctoDir}/build/TestResults/ && /workspace/cookbook/yocto/json2junit.py ${yoctoDir}/build/tmp/log/oeqa/testresults.json"
        }
        sh "cp -a ${yoctoDir}/build/TestResults* ${reportsDir}"
        junit "${archiveDir}/test_reports/${imageName}/TestResults*/*.xml"
    }
}

void runBitbakeTests(String yoctoDir) {
    sh "/workspace/cookbook/yocto/run-bitbake-tests.sh ${yoctoDir} "

}

void runYoctoCheckLayer(String yoctoDir) {
    try {
        sh "/workspace/cookbook/yocto/run-yocto-check-layer.sh ${yoctoDir} "
    } catch(e) {
        echo "Yocto compatibility check failed"
        println(e.getMessage())
    }
}


void archiveCache(String yoctoDir, boolean doArchiveCache, String yoctoCacheArchivePath) {
    def sC = sh script:"test -d ${yoctoCacheArchivePath}/sstate-cache && test -d ${yoctoCacheArchivePath}/downloads", returnStatus:true
    if (sC != 0) {
        echo "Cache dirs are not mounted"
    } else {
        echo "Cache dirs are mounted"
        if (doArchiveCache && yoctoCacheArchivePath?.trim()) {
            try {
                sh "rsync -trpgO  --info=progress2 --info=name0 --info=skip0 ${yoctoDir}/build/downloads/ ${yoctoCacheArchivePath}/downloads/"
                sh "rsync -trpgO  --info=progress2 --info=name0 --info=skip0 ${yoctoDir}/build/sstate-cache/ ${yoctoCacheArchivePath}/sstate-cache"
            } catch(e) {
                println("Error archiving cache \n" + e)
            }
        }
    }
}

void archiveImagesAndSDK(String yoctoDir, String suffix) {
    String artifactDir = "artifacts_${suffix}"

    sh "rm -rf ${artifactDir}"
    sh "mkdir ${artifactDir}"
    // Copy images and SDK to the synced directory
    sh "/workspace/ci-scripts/copy_to_archive ${yoctoDir}/build /workspace/${artifactDir}"

    // And save them in Jenkins
    try {
        archiveArtifacts "${artifactDir}/**"
    }
    catch(e) {
        println("Error archiving in Jenkins \n" + e)
    }
}

void buildWithLayer(String variantName, String imageName, String layer, String layerPath) {
    buildManifest(variantName, imageName, layer, layerPath)
}

void replaceLayer(String yoctoDir, String layerName, String newPath) {
    sh "rm -rf ${yoctoDir}/sources/${layerName}"
    sh "mv /workspace/${layerName} ${yoctoDir}/sources/${layerName}"
}

// Helper function to fetch optional boolean variables from the build environment
// If the environment variable is not set the defaultValue is used.
boolean getBoolEnvVar(String envVarName, boolean defaultValue) {
    boolean returnValue = defaultValue
    try {
        echo "Trying to fetch boolean ${envVarName}"
        returnValue = env."${envVarName}" == "true"
    } catch(e) {
        echo "${envVarName} was not set. Using default."
    }
    echo "Value for ${envVarName} is set to ${returnValue}"
    return returnValue
}

// Helper function to fetch optional string variables from the build environment
// If the environment variable is not set the defaultValue is used.
String getStringEnvVar(String envVarName, String defaultValue) {
    String returnValue = defaultValue
    try {
        echo "Trying to fetch string ${envVarName}"
        returnValue = env."${envVarName}"
        if (returnValue == null) {
            throw new Exception("The result of ${envVarName} was null")
        }
    } catch(e) {
        println(e)
        echo "${envVarName} was not set. Using default."
        returnValue = defaultValue
    }
    echo "Value for ${envVarName} is ${returnValue}"
    return returnValue
}

void buildManifest(String variantName, String imageName, String layerToReplace="", String newLayerPath="") {
    String yoctoDirInWorkspace = "pelux_yocto"
    String yoctoDir = "/workspace/${yoctoDirInWorkspace}" // On bind mount to avoid overlay2 fs.
    String manifest = "pelux.xml"
    String yoctoCacheURL = getStringEnvVar("YOCTO_CACHE_URL", "file:///var/yocto-cache")
    String yoctoCacheArchivePath = getStringEnvVar("YOCTO_CACHE_PATH", "/var/yocto-cache")

    gitSubmoduleUpdate()

    try {
        // Initialize cookbook and repo manifest
        stage("Repo init") {
            repoInit(manifest, yoctoDir)
        }

        if (layerToReplace != "" && newLayerPath != "") {
            replaceLayer(yoctoDir, layerToReplace, newLayerPath)
        }

        // Setup yocto
        String templateConf="${yoctoDir}/sources/meta-pelux/conf/variant/${variantName}"
        boolean analyzeImage = getBoolEnvVar("ANALYZE_IMAGE", false)
        boolean doArchiveCache = getBoolEnvVar("ARCHIVE_CACHE", false)
        boolean smokeTests = getBoolEnvVar("SMOKE_TEST", false)
        boolean bitbakeTests = getBoolEnvVar("BITBAKE_TEST", false)
        boolean yoctoCompatTest = getBoolEnvVar("YOCTO_COMPATIBILITY_TEST", false)
        stage("Setup bitbake and cache") {
            setupBitbake(yoctoDir, templateConf, doArchiveCache, smokeTests, analyzeImage)
            setupCache(yoctoDir, yoctoCacheURL)
        }

        // Build the images
        try {
            stage("Bitbake ${imageName} for ${variantName}") {
                buildImage(yoctoDir, imageName, variantName)
            }

            boolean buildUpdate = variantName.startsWith("rpi") || variantName.startsWith("intel") || variantName.startsWith("arp")
            stage("Bitbake cpio/swu archive for ${variantName}") {
                when (buildUpdate) {
                    sh "/workspace/cookbook/yocto/build-images.sh ${yoctoDir} ${imageName}-update"
                }
            }

            boolean buildSDK = getBoolEnvVar("BUILD_SDK", false)
            stage("Build SDK ${imageName}") {
                when (buildSDK) {
                    sh "/workspace/cookbook/yocto/build-sdk.sh ${yoctoDir} ${imageName}"
                }
            }

            if (yoctoCompatTest) {
                runYoctoCheckLayer(yoctoDir)
            }
            if (smokeTests) {
                stage ("Run smoke tests")
                runSmokeTests(yoctoDir, imageName)
                stage("Run bitbake tests"){
                    when (bitbakeTests) {
                        runBitbakeTests(yoctoDir)
                    }
                }
            }
        } finally {
            // Archive cache even if there were errors.
            stage("Archive cache") {
                when (doArchiveCache) {
                    archiveCache(yoctoDir, doArchiveCache, yoctoCacheArchivePath)
                }
            }

            // Check if we want to store the images, SDK and artifacts as well
            boolean doArchiveArtifacts = getBoolEnvVar("ARCHIVE_ARTIFACTS", false)
            stage("Archive build artifacts") {
                when (doArchiveArtifacts) {
                    echo "ARCHIVE_ARTIFACTS was set"
                    archiveImagesAndSDK(yoctoDir, variantName)
                }
            }
        }

    } finally {
        stage("Cleanup workspace") {
            sh "rm -rf ${yoctoDirInWorkspace}"
            if (currentBuild.result == "SUCCESS") {
                cleanWs()
            }
        }
    }
}

return this;
