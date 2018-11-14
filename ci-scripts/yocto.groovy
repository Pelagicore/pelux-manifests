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

        // Pull Docker container from Docker Hub
        sh "docker pull pelagicore/pelux_ubuntu1604"

        // Tag container as 'pelux'
        sh "docker tag pelagicore/pelux_ubuntu1604 pelux"

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

void setupBitbake(String yoctoDir, String templateConf, boolean archive, boolean smokeTests=false) {
    stage("Setup bitbake") {
        vagrant("/vagrant/cookbook/yocto/initialize-bitbake.sh ${yoctoDir} ${templateConf}")

        // Add other settings that are CI specific to the local.conf
        vagrant("cat /vagrant/local.conf.appendix >> ${yoctoDir}/build/conf/local.conf")

        // Add settings for smoke testing if needed
        if (smokeTests) {
            stage("Setup local conf for smoke testing and tests export") {
                vagrant("echo '' >> ${yoctoDir}/build/conf/local.conf")
                vagrant("cat /vagrant/test-scripts/local.conf.appendix >> ${yoctoDir}/build/conf/local.conf")
            }
        }
        if (archive){
            //Mirror git repositories
            vagrant("cat /vagrant/local.conf_mirror.appendix >> ${yoctoDir}/build/conf/local.conf")

        }
    }
}

void setupCache(String yoctoDir, String url) {
    stage("Setup cache") {
        // Setup site.conf if not building the master to do a incremental build.
        // The YOCTO_CACHE_URL can be set globally in Manage Jenkins -> Configure System -> Global Properties
        // or for one job as a parameter.
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

    boolean nightly = env.NIGHTLY_BUILD == "true"
    boolean weekly = env.WEEKLY_BUILD == "true"
    if (nightly || weekly) {
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


void archiveCache(String yoctoDir, boolean archive, String archivePath) {
    if (archive && archivePath?.trim()) {
        stage("Archive cache") {
            vagrant("rsync -trpgO ${yoctoDir}/build/downloads/ ${archivePath}/downloads/")
            vagrant("rsync -trpgO ${yoctoDir}/build/sstate-cache/ ${archivePath}/sstate-cache")
        }
    }
}

void archiveArtifacts(String yoctoDir, String suffix) {
    stage("Archive artifacts") {
        String artifactDir = "artifacts_${suffix}"

        sh "rm -rf ${artifactDir}"
        sh "mkdir ${artifactDir}"

        // Copy images and SDK to the synced directory
        vagrant("/vagrant/ci-scripts/copy_to_archive ${yoctoDir}/build /vagrant/${artifactDir}")

        // And save them in Jenkins
        archiveArtifacts "${artifactDir}/**"
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

void buildManifest(String variantName, String imageName, String layerToReplace="", String newLayerPath="") {
    String yoctoDirInWorkspace = "pelux_yocto"
    String yoctoDir = "/vagrant/${yoctoDirInWorkspace}" // On bind mount to avoid overlay2 fs.
    String manifest = "pelux.xml"

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
        boolean archive = env.ARCHIVE_CACHE == "true"
        boolean smokeTests = env.SMOKE_TEST == "true"
        setupBitbake(yoctoDir, templateConf, archive, smokeTests)
        setupCache(yoctoDir, env.YOCTO_CACHE_URL)

        // Build the images
        try {
            boolean buildUpdate = variantName.startsWith("rpi")
            buildImageAndSDK(yoctoDir, imageName, variantName, buildUpdate)
            runYoctoCheckLayer(yoctoDir)
            if (smokeTests) {
                boolean weekly = env.WEEKLY_BUILD == "true"
                runSmokeTests(yoctoDir, imageName)
                if(weekly) {
                    runBitbakeTests(yoctoDir)
                }
            }

        } finally { // Archive cache even if there were errors.
            archiveCache(yoctoDir, archive, env.YOCTO_CACHE_ARCHIVE_PATH)
            // If nightly build, we store the artifacts as well
            boolean nightly = env.NIGHTLY_BUILD == "true"
            boolean weekly = env.WEEKLY_BUILD == "true"
            if (nightly || weekly) {
                archiveArtifacts(yoctoDir, variantName)
            }
        }
    } finally {
        shutdownVagrant()
        deleteYoctoBuildDir("${yoctoDirInWorkspace}")
    }
}

return this;
