#!/usr/bin/groovy

// Copyright (C) Luxoft Sweden AB 2018

// Helper function to run commands through vagrant
int vagrant(String command, boolean saveStatus=false) {
    sh returnStatus: saveStatus, script: "vagrant ssh -c \"${command}\""
}

// Helper to do checkout and update submodules
void checkoutWithSubmodules() {
    // Checkout the git repository and refspec pointed to by jenkins
    stage("Checkout") {
        checkout scm
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

void repoInit(String manifest) {
    stage("Repo init") {
        vagrant("/vagrant/ci-scripts/do_repo_init ${manifest}")
    }
}

void setupBitbake(String yoctoDir, String templateConf, boolean smokeTests=false) {
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

void buildImageAndSDK(String yoctoDir, String imageName, boolean update=false) {
    // If we have a site.conf, that means we have caching, if we don't that
    // means we should do a fetchall.
    if (vagrant("test -f ${yoctoDir}/build/conf/site.conf", true) != 0) {
        stage("Fetch sources") {
            vagrant("/vagrant/cookbook/yocto/fetch-sources-for-recipes.sh ${yoctoDir} ${imageName}")
        }
    } else {
        echo "\'site.conf\' exists, using cache"
    }

    stage("Bitbake ${imageName}") {
        vagrant("/vagrant/cookbook/yocto/build-images.sh ${yoctoDir} ${imageName}")
        vagrant("/vagrant/cookbook/yocto/build-images.sh ${yoctoDir} ${imageName}-dev")

        if (update) {
            stage("Bitbake Update ${imageName}") {
                vagrant("/vagrant/cookbook/yocto/build-images.sh ${yoctoDir} ${imageName}-update")
                vagrant("/vagrant/cookbook/yocto/build-images.sh ${yoctoDir} ${imageName}-dev-update")
            }
        }
    }

    boolean nightly = env.NIGHTLY_BUILD == "true"
    boolean weekly = env.WEEKLY_BUILD == "true"
    if (nightly || weekly) {
        stage("Build SDK ${imageName}") {
            vagrant("/vagrant/cookbook/yocto/build-sdk.sh ${yoctoDir} ${imageName}")
            vagrant("/vagrant/cookbook/yocto/build-sdk.sh ${yoctoDir} ${imageName}-dev")
        }
    }
}

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
    String archiveDir = "bitbake_TestReport"

    try {
        stage("Perform Bitbake Testing"){
            vagrant("/vagrant/cookbook/yocto/run-bitbake-tests.sh")
        }
    } catch (e){
	   echo "Bitbake tests failed"
	   println(e.getMessage())
    } finally{
        stage("Publish smoke test results") {
            reportsDir="/vagrant/${archiveDir}/test_reports/bitbake_tests/"
            vagrant("mkdir -p ${reportsDir}")
            vagrant("cp -a ${yoctoDir}/build/TestResults* ${reportsDir}")
            junit "${archiveDir}/test_reports/bitbake_Tests/TestResults*/*.xml"
        }
    }
}


void archiveCache(String yoctoDir, boolean archive, String archivePath) {
    if (archive && archivePath?.trim()) {
        stage("Archive cache") {
            vagrant("rsync -trpg ${yoctoDir}/build/downloads/ ${archivePath}/downloads/")
            vagrant("rsync -trpg ${yoctoDir}/build/sstate-cache/ ${archivePath}/sstate-cache")
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
    // Store the directory we are executed in as our workspace.
    String yoctoDir = "/home/yoctouser/pelux_yocto"
    String manifest = "pelux.xml"

    // We can't do "checkout scm" here since pelux-manifests is not the
    // canonical repo here.
    sh "git submodule update --init"
    try {
        // Initialize vagrant and repo manifest
        startVagrant()
        repoInit(manifest)

        replaceLayer(yoctoDir, layer, layerPath)

        // Setup yocto
        String templateConf="${yoctoDir}/sources/meta-pelux/conf/variant/${variantName}"
        setupBitbake(yoctoDir, templateConf)
        setupCache(yoctoDir, env.YOCTO_CACHE_URL)

        // Build the images
        try {
            boolean buildUpdate = variantName.startsWith("rpi")
            buildImageAndSDK(yoctoDir, imageName, buildUpdate)
        } finally { // Archive cache even if there were errors.
            boolean archive = env.ARCHIVE_CACHE == "true"
            archiveCache(yoctoDir, archive, env.YOCTO_CACHE_ARCHIVE_PATH)
        }
    } finally {
        shutdownVagrant()
    }
}

void replaceLayer(String yoctoDir, String layerName, String newPath) {
    vagrant("rm -rf ${yoctoDir}/sources/${layerName}")
    sh "cp -R ${newPath} ${layerName}"
    vagrant("mv /vagrant/${layerName} ${yoctoDir}/sources/")
}

void buildManifest(String variantName, String imageName, boolean smokeTests=false) {
    // Store the directory we are executed in as our workspace.
    String yoctoDir = "/home/yoctouser/pelux_yocto"
    String manifest = "pelux.xml"

    // Initialize code
    checkoutWithSubmodules()
    try {
        // Initialize vagrant and repo manifest
        startVagrant()
        repoInit(manifest)

        // Setup yocto
        String templateConf="${yoctoDir}/sources/meta-pelux/conf/variant/${variantName}"
        setupBitbake(yoctoDir, templateConf, smokeTests)
        setupCache(yoctoDir, env.YOCTO_CACHE_URL)

        // Build the images
        try {
            boolean buildUpdate = variantName.startsWith("rpi")
            buildImageAndSDK(yoctoDir, imageName, buildUpdate)
            if (smokeTests) {
                boolean weekly = env.WEEKLY_BUILD == "true"
                runSmokeTests(yoctoDir, imageName)
                if(weekly) {
                    runBitbakeTests(yoctoDir)
                }
            }

        } finally { // Archive cache even if there were errors.
            boolean archive = env.ARCHIVE_CACHE == "true"
            archiveCache(yoctoDir, archive, env.YOCTO_CACHE_ARCHIVE_PATH)

            // If nightly build, we store the artifacts as well
            boolean nightly = env.NIGHTLY_BUILD == "true"
            if (nightly) {
                archiveArtifacts(yoctoDir, variantName)
            }
        }
    } finally {
        shutdownVagrant()
    }
}

return this;
