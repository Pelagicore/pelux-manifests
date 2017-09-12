#!/usr/bin/groovy

// Copyright (C) Pelagicore AB 2017
def buildManifest = {String manifest, String bitbake_image ->
    // Store the directory we are executed in as our workspace.
    String workspace = pwd()

    // Stages are subtasks that will be shown as subsections of the finished build in Jenkins.

    stage("Checkout ${bitbake_image}") {
        // Checkout the git repository and refspec pointed to by jenkins
        checkout scm
        // Update the submodules in the repository.
        sh 'git submodule update --init'
    }

    stage("Start Vagrant ${bitbake_image}") {
        // Start the machine (destroy it if present) and provision it
        sh "cd ${workspace}"
        sh "vagrant destroy -f || true"
        sh "vagrant up"
    }

    String yoctoDir = "/home/vagrant/pelux_yocto"

    stage("Repo init ${bitbake_image}") {
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

    stage("Setup bitbake and do fetchall ${bitbake_image}") {
        // Extract the BSP part of the manifest file name.
        String bsp = manifest.split("-")[1].tokenize(".")[0]
        // Setup bitbake environment and trigger a 'fetchall'
        sh "vagrant ssh -c \"TEMPLATECONF=${yoctoDir}/sources/meta-pelux-bsp-${bsp}/conf /vagrant/vagrant-cookbook/yocto/fetch-sources-for-recipes.sh ${yoctoDir} ${bitbake_image}\""
    }

    stage("Bitbake ${bitbake_image}") {
        sh "vagrant ssh -c \"/vagrant/vagrant-cookbook/yocto/build-images.sh ${yoctoDir} ${bitbake_image}\""
    }

    stage("Copy images and cache ${bitbake_image}") {
        sh "rm -rf archive"
        sh "mkdir -p archive"
        sh "vagrant ssh -c \"cp -a ${yoctoDir}/build/tmp/deploy/images/ /vagrant/archive/\""

        // Archive the downloads and sstate when the environment variable was set to true
        // by the Jenkins job.
        if (env.ARCHIVE_CACHE) {
            sh "vagrant ssh -c \"cp -a ${yoctoDir}/build/downloads/ /vagrant/archive/\""
            sh "vagrant ssh -c \"cp -a ${yoctoDir}/build/sstate-cache/ /vagrant/archive/\""
        }
    }

    stage("Archive ${bitbake_image}") {
        archiveArtifacts artifacts: "archive/**/*"
    }

    // Always try to shut down the machine
    // Shutdown the machine
    sh "vagrant destroy -f || true"
}

// Run the different jobs in parallel, on different slaves
parallel 'core':{
    node("DockerCI") { buildManifest("pelux-intel.xml", "core-image-pelux") }
},'qtauto':{
    node("DockerCI") { buildManifest("pelux-intel-qtauto.xml", "core-image-pelux-qtauto") }
}
