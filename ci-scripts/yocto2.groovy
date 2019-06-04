#!/usr/bin/groovy

// Copyright (C) Luxoft Sweden AB 2018

// Helper to do checkout and update of submodules.
void gitSubmoduleUpdate() {
    stage("Update submodules") {
        sh "git submodule update --init"
    }
}

void repoInit(String manifest, String yoctoDir) {
    stage("Repo init") {
        syncDir = "/workspace"
        sh "/workspace/ci-scripts/do_repo_init ${manifest} ${syncDir} ${yoctoDir}"
    }
}

void setupBitbake(String yoctoDir, String templateConf, boolean doArchiveCache, boolean smokeTests, boolean analyzeImage) {
    stage("Setup bitbake") {
        sh "/workspace/cookbook/yocto/initialize-bitbake.sh ${yoctoDir} ${templateConf}"

        // Add other settings that are CI specific to the local.conf
        sh "cat /workspace/conf/local.conf.appendix >> ${yoctoDir}/build/conf/local.conf"
        // Add settings for smoke testing if needed
        if (smokeTests) {
            stage("Setup local conf for smoke testing and tests export") {
                sh "echo '' >> ${yoctoDir}/build/conf/local.conf"
                sh "cat /workspace/conf/test-scripts/local.conf.appendix >> ${yoctoDir}/build/conf/local.conf"
            }
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
}

void setupCache(String yoctoDir, String url) {
    stage("Setup cache") {
        // Setup site.conf if not building the master to do a incremental build.
        // The YOCTO_CACHE_URL can be set for a Jenkins job as a parameter.
        if (url?.trim()) {
            sh "sed 's|%CACHEURL%|${url}|g' /workspace/site.conf.in > ${yoctoDir}/build/conf/site.conf"
            echo "Cache set up"
        } else {
            echo "No cache setup"
        }
    }
}

void buildImageAndSDK(String yoctoDir, String imageName, String variantName, boolean update=false) {
    // If we have a site.conf, that means we have caching, if we don't that
    // means we should do a fetchall.
    def statusCode = sh script:"test -f ${yoctoDir}/build/conf/site.conf", returnStatus:true
    if (statusCode != 0) {
        stage("Fetch sources") {
            sh "/workspace/cookbook/yocto/fetch-sources-for-recipes.sh ${yoctoDir} ${imageName}"
        }
    } else {
        echo "\'site.conf\' exists, using cache"
    }

    stage("Bitbake ${imageName} for ${variantName}") {
        sh "/workspace/cookbook/yocto/build-images.sh ${yoctoDir} ${imageName}"

        if (update) {
            stage("Bitbake Update ${imageName} for ${variantName}") {
                sh "/workspace/cookbook/yocto/build-images.sh ${yoctoDir} ${imageName}-update"
            }
        }
    }

    boolean buildSDK = getBoolEnvVar("BUILD_SDK", false)
    if (buildSDK) {
        stage("Build SDK ${imageName}") {
            sh "/workspace/cookbook/yocto/build-sdk.sh ${yoctoDir} ${imageName}"
        }
    }
}

// In order to run smoke tests, the -dev image should be specified because of the dependencies
void runSmokeTests(String yoctoDir, String imageName) {
    String archiveDir = "testReports-" + imageName

    try {
        stage("Perform smoke testing") {
            sh "/workspace/cookbook/yocto/runqemu-smoke-test.sh ${yoctoDir} ${imageName}"
        }
    } catch(e) {
        echo "There were failing tests"
        println(e.getMessage())
    } finally {
        stage("Publish smoke test results") {
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
}

void runBitbakeTests(String yoctoDir) {
    stage("Perform Bitbake Testing"){
       sh "/workspace/cookbook/yocto/run-bitbake-tests.sh ${yoctoDir} "
    }
}

void runYoctoCheckLayer(String yoctoDir) {
    try {
        stage("Perform Yocto Compatibility Check"){
            sh "/workspace/cookbook/yocto/run-yocto-check-layer.sh ${yoctoDir} "
        }
    } catch(e) {
        echo "Yocto compatibility check failed"
        println(e.getMessage())
    }
}


void archiveCache(String yoctoDir, boolean doArchiveCache, String yoctoCacheArchivePath) {
    if (doArchiveCache && yoctoCacheArchivePath?.trim()) {
        stage("Archive cache") {
            sh "rsync -trpgO ${yoctoDir}/build/downloads/ ${yoctoCacheArchivePath}/downloads/"
            sh "rsync -trpgO ${yoctoDir}/build/sstate-cache/ ${yoctoCacheArchivePath}/sstate-cache"
        }
    }
}

void archiveImagesAndSDK(String yoctoDir, String suffix) {
    stage("Archive Images, SDK and save to Jenkins") {
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
}

void buildWithLayer(String variantName, String imageName, String layer, String layerPath) {
    buildManifest(variantName, imageName, layer, layerPath)
}

void replaceLayer(String yoctoDir, String layerName, String newPath) {
    sh "rm -rf ${yoctoDir}/sources/${layerName}"
    sh "mv /workspace/${layerName} ${yoctoDir}/sources/${layerName}"
}

void deleteYoctoBuildDir(String buildDir) {
    stage("Deleting Yocto build directory") {
        sh "rm -rf ${buildDir}"
    }
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
        repoInit(manifest, yoctoDir)

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
        setupBitbake(yoctoDir, templateConf, doArchiveCache, smokeTests, analyzeImage)
        setupCache(yoctoDir, yoctoCacheURL)

        // Build the images
        try {
            boolean buildUpdate = variantName.startsWith("rpi")
            buildImageAndSDK(yoctoDir, imageName, variantName, buildUpdate)
            if (yoctoCompatTest) {
                runYoctoCheckLayer(yoctoDir)
            }
            if (smokeTests) {
                runSmokeTests(yoctoDir, imageName)
                if(bitbakeTests) {
                    runBitbakeTests(yoctoDir)
                }
            }

        } finally {
            // Archive cache even if there were errors.
            archiveCache(yoctoDir, doArchiveCache, yoctoCacheArchivePath)

            // Check if we want to store the images, SDK and artifacts as well
            boolean doArchiveArtifacts = getBoolEnvVar("ARCHIVE_ARTIFACTS", false)
            if (doArchiveArtifacts) {
                echo "ARCHIVE_ARTIFACTS was set"
                archiveImagesAndSDK(yoctoDir, variantName)
            }
        }

    } finally {
        deleteYoctoBuildDir("${yoctoDirInWorkspace}")
    }
}

return this;
