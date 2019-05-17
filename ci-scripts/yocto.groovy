#!/usr/bin/groovy

// Copyright (C) Luxoft Sweden AB 2018

// Helper function to run commands through vagrant
int vagrant(String command, boolean saveStatus=false) {
    sh returnStatus: saveStatus, script: "vagrant ssh -c \"${command}\""
}

// Helper to do checkout and update of submodules.
void gitSubmoduleUpdate() {
    stage("Update submodules") {
        sh "git submodule update --init"
    }
}

// Start the machine (destroy it if present) and provision it
void startVagrant(boolean kill=true) {
    stage("Start vagrant") {
        if (kill) {
            shutdownVagrant()
        }

        sh "vagrant up"
    }
}

void shutdownVagrant() {
    stage("Shutdown vagrant") {
        retry(5) {
            sh "vagrant destroy -f"
        }
    }
}

void repoInit(String manifest, String yoctoDir) {
    stage("Repo init") {
        syncDir = "/vagrant"
        vagrant("/vagrant/ci-scripts/do_repo_init ${manifest} ${syncDir} ${yoctoDir}")
    }
}

void setupBitbake(String yoctoDir, String templateConf, boolean doArchiveCache, boolean analyzeImage) {
    stage("Setup bitbake") {
        vagrant("/vagrant/cookbook/yocto/initialize-bitbake.sh ${yoctoDir} ${templateConf}")

        // Add other settings that are CI specific to the local.conf
        vagrant("cat /vagrant/conf/local.conf.appendix >> ${yoctoDir}/build/conf/local.conf")

        if (doArchiveCache){
            //Mirror git repositories
            vagrant("cat /vagrant/conf/local.conf_mirror.appendix >> ${yoctoDir}/build/conf/local.conf")

        }
        if (analyzeImage){
            //Set up the configuration for running ISAFW
            vagrant ("cat /vagrant/conf/isafw.local.conf >> ${yoctoDir}/build/conf/local.conf")
            vagrant ("cat /vagrant/conf/isafw.bblayers.conf >> ${yoctoDir}/build/conf/bblayers.conf")
        }
    }
}

void setupCache(String yoctoDir, String url) {
    stage("Setup cache") {
        // Setup site.conf if not building the master to do a incremental build.
        // The YOCTO_CACHE_URL can be set for a Jenkins job as a parameter.
        if (url?.trim()) {
            vagrant("sed 's|%CACHEURL%|${url}|g' /vagrant/site.conf.in > ${yoctoDir}/build/conf/site.conf")
            echo "Cache set up"
        } else {
            echo "No cache setup"
        }
    }
}

void buildImageAndSDK(String yoctoDir, String imageName, String variantName, boolean update=false) {
    // If we have a site.conf, that means we have caching, if we don't that
    // means we should do a fetchall.
    if (vagrant("test -f ${yoctoDir}/build/conf/site.conf", true) != 0) {
        stage("Fetch sources") {
            vagrant("/vagrant/cookbook/yocto/fetch-sources-for-recipes.sh ${yoctoDir} ${imageName}")
        }
    } else {
        echo "\'site.conf\' exists, using cache"
    }

    stage("Bitbake ${imageName} for ${variantName}") {
        vagrant("/vagrant/cookbook/yocto/build-images.sh ${yoctoDir} ${imageName}")

        if (update) {
            stage("Bitbake Update ${imageName} for ${variantName}") {
                vagrant("/vagrant/cookbook/yocto/build-images.sh ${yoctoDir} ${imageName}-update")
            }
        }
    }

    boolean buildSDK = getBoolEnvVar("BUILD_SDK", false)
    if (buildSDK) {
        stage("Build SDK ${imageName}") {
            vagrant("/vagrant/cookbook/yocto/build-sdk.sh ${yoctoDir} ${imageName}")
        }
    }
}

// In order to run smoke tests, the -dev image should be specified because of the dependencies
void runSmokeTests(String yoctoDir, String imageName) {
    String archiveDir = "testReports-" + imageName

    try {
        stage("Perform smoke testing") {
            vagrant("/vagrant/cookbook/yocto/runqemu-smoke-test.sh ${yoctoDir} ${imageName}")
        }
    } catch(e) {
        echo "There were failing tests"
        println(e.getMessage())
    } finally {
        stage("Publish smoke test results") {
            reportsDir="/vagrant/${archiveDir}/test_reports/${imageName}/"
            vagrant("mkdir -p ${reportsDir}")
            vagrant("cp -a ${yoctoDir}/build/TestResults* ${reportsDir}")
            junit "${archiveDir}/test_reports/${imageName}/TestResults*/*.xml"
        }
    }
}

void runBitbakeTests(String yoctoDir) {
    stage("Perform Bitbake Testing"){
        vagrant("/vagrant/cookbook/yocto/run-bitbake-tests.sh ${yoctoDir} ")
    }
}

void runYoctoCheckLayer(String yoctoDir) {
    try {
        stage("Perform Yocto Compatibility Check"){
            vagrant("/vagrant/cookbook/yocto/run-yocto-check-layer.sh ${yoctoDir} ")
        }
    } catch(e) {
        echo "Yocto compatibility check failed"
        println(e.getMessage())
    }
}


void archiveCache(String yoctoDir, boolean doArchiveCache, String yoctoCacheArchivePath) {
    if (doArchiveCache && yoctoCacheArchivePath?.trim()) {
        stage("Archive cache") {
            vagrant("rsync -trpgO ${yoctoDir}/build/downloads/ ${yoctoCacheArchivePath}/downloads/")
            vagrant("rsync -trpgO ${yoctoDir}/build/sstate-cache/ ${yoctoCacheArchivePath}/sstate-cache")
        }
    }
}

void archiveImagesAndSDK(String yoctoDir, String suffix) {
    stage("Archive Images, SDK and save to Jenkins") {
        String artifactDir = "artifacts_${suffix}"

        sh "rm -rf ${artifactDir}"
        sh "mkdir ${artifactDir}"
        // Copy images and SDK to the synced directory
        vagrant("/vagrant/ci-scripts/copy_to_archive ${yoctoDir}/build /vagrant/${artifactDir}")

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
    vagrant("rm -rf ${yoctoDir}/sources/${layerName}")
    sh "cp -R ${newPath} ${layerName}"
    vagrant("mv /vagrant/${layerName} ${yoctoDir}/sources/")
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
    String yoctoDir = "/vagrant/${yoctoDirInWorkspace}" // On bind mount to avoid overlay2 fs.
    String manifest = "pelux.xml"
    String yoctoCacheURL = getStringEnvVar("YOCTO_CACHE_URL", "file:///var/yocto-cache")
    String yoctoCacheArchivePath = getStringEnvVar("YOCTO_CACHE_PATH", "/var/yocto-cache")

    gitSubmoduleUpdate()

    try {
        // Initialize vagrant and repo manifest
        startVagrant()
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
        setupBitbake(yoctoDir, templateConf, doArchiveCache, analyzeImage)
        setupCache(yoctoDir, yoctoCacheURL)

        // Build the images
        try {
            boolean buildUpdate = variantName.startsWith("rpi") || variantName.startsWith("intel") || variantName.startsWith("arp")
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

        }  catch(e) {
            echo "Bitbake process failed!"
            println(e.getMessage())
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
        shutdownVagrant()
        deleteYoctoBuildDir("${yoctoDirInWorkspace}")
    }
}

return this;
