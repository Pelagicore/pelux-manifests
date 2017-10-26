#!/usr/bin/groovy

// Copyright (C) Pelagicore AB 2017

/*
 * Supported values for bsp are "intel" or "rpi"
 * Supported values for qtauto are true or false
 */
def buildManifest = {String bsp, boolean qtauto ->
    // Store the directory we are executed in as our workspace.
    String workspace = pwd()
    String yoctoDir = "/home/vagrant/pelux_yocto"

    // From BSP name and qtauto values we can deduce what image to build
    String bitbake_image = "core-image-pelux-" + (qtauto ? "qtauto-neptune" : "minimal")
    // And how to present it in Jenkins
    String stage_name = bsp + (qtauto ? "-qtauto" : "")

    // These could be empty, so check for that when using them.
    environment {
        YOCTO_CACHE_URL = "${env.YOCTO_CACHE_URL}"
        YOCTO_CACHE_ARCHIVE_PATH = "${env.YOCTO_CACHE_ARCHIVE_PATH}"
    }

    // Stages are subtasks that will be shown as subsections of the finished build in Jenkins.

    stage("Checkout ${stage_name}") {
        // Checkout the git repository and refspec pointed to by jenkins
        checkout scm
        // Update the submodules in the repository.
        sh 'git submodule update --init'
    }

    stage("Start Vagrant ${stage_name}") {
        // Start the machine (destroy it if present) and provision it
        sh "cd ${workspace}"
        sh "vagrant destroy -f || true"
        sh "vagrant up"
    }

    stage("Repo init ${stage_name}") {
        String manifest = "pelux.xml"
        sh "pwd"
        sh "ls -la"
        sh "vagrant ssh -c \"/vagrant/ci-scripts/do_repo_init ${manifest}\""

        // Setup site.conf if not building the master to do a incremental build.
        // The YOCTO_CACHE_URL can be set globaly in Manage Jenkins -> Configure System -> Global Properties
        // or for one job as a parameter.
        if (env.YOCTO_CACHE_URL?.trim()) {
           sh "vagrant ssh -c \"mkdir -p  ${yoctoDir}/build/conf/\""
           sh "vagrant ssh -c \"sed 's|%CACHEURL%|${env.YOCTO_CACHE_URL}|g' /vagrant/site.conf.in > ${yoctoDir}/build/conf/site.conf\""
        }
    }

    stage("Setup bitbake ${stage_name}") {
        // Setup bitbake environment
        confdir = "conf"
        if (qtauto) {
            confdir += "-qt"
        }
        templateconf="${yoctoDir}/sources/meta-pelux/meta-${bsp}-extras/${confdir} "
        sh "vagrant ssh -c \"/vagrant/vagrant-cookbook/yocto/initialize-bitbake.sh ${yoctoDir} ${templateconf}\""
    }

    stage("Do fetchall ${stage_name}") {
        // Without cache access, we do want to do fetchall, but only then.
        if (!env.YOCTO_CACHE_URL?.trim()) {
            sh "vagrant ssh -c \"/vagrant/vagrant-cookbook/yocto/fetch-sources-for-recipes.sh ${yoctoDir} ${bitbake_image}\""
        }
    }

    stage("Bitbake ${stage_name}") {
        sh "vagrant ssh -c \"/vagrant/vagrant-cookbook/yocto/build-images.sh ${yoctoDir} ${bitbake_image}\""
    }

    stage("Archive cache ${stage_name}") {
        // Archive the downloads and sstate when the environment variable was set to true
        // by the Jenkins job.
        if (env.ARCHIVE_CACHE && env.YOCTO_CACHE_ARCHIVE_PATH?.trim()) {
            sh "vagrant ssh -c \"rsync -trpg ${yoctoDir}/build/downloads/ ${env.YOCTO_CACHE_ARCHIVE_PATH}/downloads/\""
            sh "vagrant ssh -c \"rsync -trpg ${yoctoDir}/build/sstate-cache/ ${env.YOCTO_CACHE_ARCHIVE_PATH}/sstate-cache\""
        }
    }

    // Always try to shut down the machine
    // Shutdown the machine
    sh "vagrant destroy -f || true"
}

// Run the different jobs in parallel, on different slaves
parallel 'intel':{
    node("DockerCI") { buildManifest("intel", false) }
},'intel-qtauto':{
    node("DockerCI") { buildManifest("intel", true) }
},'rpi':{
    node("DockerCI") { buildManifest("rpi", false) }
}
