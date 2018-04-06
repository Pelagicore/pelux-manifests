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

void setupBitbake(String yoctoDir, String templateConf) {
    stage("Setup bitbake") {
        vagrant("/vagrant/cookbook/yocto/initialize-bitbake.sh ${yoctoDir} ${templateConf}")

        // Add other settings that are CI specific to the local.conf
        vagrant("cat /vagrant/local.conf.appendix >> ${yoctoDir}/build/conf/local.conf")
    }
}

void setupCache(String yoctoDir, String url) {
    stage("Setup cache") {
        // Setup site.conf if not building the master to do a incremental build.
        // The YOCTO_CACHE_URL can be set globally in Manage Jenkins -> Configure System -> Global Properties
        // or for one job as a parameter.
        if (url?.trim()) {
            vagrant("sed 's|%CACHEURL%|${url}|g' /vagrant/site.conf.in > ${yoctoDir}/build/conf/site.conf")
        } else {
            echo "No cache setup"
        }
    }
}

void buildImageAndSDK(String yoctoDir, String imageName, boolean dev=true) {
    // If we have a site.conf, that means we have caching, if we don't that
    // means we should do a fetchall.
    if (vagrant("test -f ${yoctoDir}/build/conf/site.conf", true) != 0) {
        stage("Fetch sources") {
            vagrant("/vagrant/cookbook/yocto/fetch-sources-for-recipes.sh ${yoctoDir} ${bitbake_image}")
        }
    }

    stage("Bitbake ${imageName}") {
        vagrant("/vagrant/cookbook/yocto/build-images.sh ${yoctoDir} ${imageName}")
        if (dev) {
            vagrant("/vagrant/cookbook/yocto/build-images.sh ${yoctoDir} ${imageName}-dev")
        }
    }

    stage("Build SDK ${imageName}") {
        vagrant("/vagrant/cookbook/yocto/build-sdk.sh ${yoctoDir} ${imageName}")
        if (dev) {
            vagrant("/vagrant/cookbook/yocto/build-sdk.sh ${yoctoDir} ${imageName}-dev")
        }
    }
}

void archiveCache(String yoctoDir, boolean archive, String archivePath) {
    if (archive) {
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

void buildWithLayer(String variant_name, String bitbake_image, String layer, String layerPath) {
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
        String templateConf="${yoctoDir}/sources/meta-pelux/conf/variant/${variant_name}"
        setupBitbake(yoctoDir, templateConf)
        setupCache(yoctoDir, env.YOCTO_CACHE_URL)

        // Build the images
        try {
            buildImageAndSDK(yoctoDir, bitbake_image)
        } finally { // Archive cache even if there were errors.
            archiveCache(yoctoDir, env.ARCHIVE_CACHE, env.YOCTO_CACHE_ARCHIVE_PATH)

            if (env.NIGHTLY_BUILD) {
                archiveArtifacts(yoctoDir, variant_name)
            }
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

void buildManifest(String variant_name, String bitbake_image) {
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
        String templateConf="${yoctoDir}/sources/meta-pelux/conf/variant/${variant_name}"
        setupBitbake(yoctoDir, templateConf)
        setupCache(yoctoDir, env.YOCTO_CACHE_URL)

        // Build the images
        try {
            buildImageAndSDK(yoctoDir, bitbake_image)
        } finally { // Archive cache even if there were errors.
            archiveCache(yoctoDir, env.ARCHIVE_CACHE, env.YOCTO_CACHE_ARCHIVE_PATH)

            if (env.NIGHTLY_BUILD) {
                archiveArtifacts(yoctoDir, variant_name)
            }
        }
    } finally {
        shutdownVagrant()
    }
}

return this;
