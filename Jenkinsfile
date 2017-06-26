#!/usr/bin/groovy

// Copyright (C) Pelagicore AB 2017
def buildManifest = {String manifest, String bitbake_image ->
    // Store the directory we are executed in as our workspace.
    String workspace = pwd()

    try {
        // Stages are subtasks that will be shown as subsections of the finished build in Jenkins.

        stage("Checkout ${bitbake_image}") {
            // Checkout the git repository and refspec pointed to by jenkins
            checkout scm
            // Update the submodules in the repository.
            sh 'git submodule update --init'
        }

        stage("Start Vagrant ${bitbake_image}") {
            // Calculate available amount of RAM
            String gigsramStr = sh (
                script: 'free -tg | tail -n1 | awk \'{ print $2 }\'',
                returnStdout: true
            )
            int gigsram = gigsramStr.trim() as Integer
            // Cap memory usage at 8GB
            if (gigsram >= 8) {
                gigsram = 8
            }
            println "Will set VAGRANT_RAM to ${gigsram}"

            // Start the machine (destroy it if present) and provision it
            sh "cd ${workspace}"
            sh "vagrant destroy -f || true"
            withEnv(["VAGRANT_RAM=${gigsram}"]) {
                sh "vagrant up"
            }
        }

        stage("Repo init ${bitbake_image}") {
            sh "pwd"
            sh "ls -la"
            sh "vagrant ssh -c \"/vagrant/ci-scripts/do_repo_init ${manifest}\""
        }

        String yoctoDir = "/home/vagrant/pelux_yocto"

        stage("Setup bitbake and do fetchall ${bitbake_image}") {
            // Extract the BSP part of the manifest file name.
            String bsp = manifest.split("-")[1].tokenize(".")[0]
            // Setup bitbake environment and trigger a 'fetchall'
            sh "vagrant ssh -c \"TEMPLATECONF=${yoctoDir}/sources/meta-pelux-bsp-${bsp}/conf /vagrant/vagrant-cookbook/yocto/fetch-sources-for-recipes.sh ${yoctoDir} ${bitbake_image}\""
        }

        stage("Bitbake ${bitbake_image}") {
            sh "vagrant ssh -c \"/vagrant/vagrant-cookbook/yocto/build-images.sh ${yoctoDir} ${bitbake_image}\""
        }
    }

    catch(err) {
        // Do not add a stage here.
        // When "stage" commands are run in a different order than the previous run
        // the history is hidden since the rendering plugin assumes that the system has changed and
        // that the old runs are irrelevant. As such adding a stage at this point will trigger a
        // "change of the system" each time a run fails.
        println "Something went wrong!"
        println err
        currentBuild.result = "FAILURE"
    }

    // Always try to shut down the machine
    // Shutdown the machine
    sh "vagrant destroy -f || true"
}

// Run the different jobs in parallel, on different slaves
parallel 'core':{
    node("DockerCI") { buildManifest("pelux-intel.xml", "core-image-pelux") }
},'qtauto':{
    node("DockerCI") { buildManifest("pelux-intel-qt.xml", "core-image-pelux-qt") }
}