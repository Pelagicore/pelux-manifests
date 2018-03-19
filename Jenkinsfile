#!/usr/bin/groovy

// Copyright (C) Pelagicore AB 2017

// Helper function to run commands through vagrant
void vagrant(String command) {
    sh "vagrant ssh -c \"${command}\""
}

/*
 * Supported values for bsp are "intel" or "rpi"
 * Supported values for qtauto are true or false
 */
void buildManifest(String variant_name, String bitbake_image) {
    // Store the directory we are executed in as our workspace.
    String yoctoDir = "/home/yoctouser/pelux_yocto"
    String manifest = "pelux.xml"
    String archive_directory = "archive_" + bitbake_image + "-" + variant_name

    // Everything we run here runs in a docker container handled by Vagrant
    node("Yocto") {

        // These could be empty, so check for that when using them.
        environment {
            YOCTO_CACHE_URL = "${env.YOCTO_CACHE_URL}"
            YOCTO_CACHE_ARCHIVE_PATH = "${env.YOCTO_CACHE_ARCHIVE_PATH}"
        }

        // Stages are subtasks that will be shown as subsections of the finished
        // build in Jenkins.

        stage("Checkout ${variant_name}") {
            // Checkout the git repository and refspec pointed to by jenkins
            checkout scm
            // Update the submodules in the repository.
            sh 'git submodule update --init'
        }

        stage("Start Vagrant ${variant_name}") {
            // Start the machine (destroy it if present) and provision it
            sh "vagrant destroy -f || true"
            sh "vagrant up"
        }

        stage("Repo init ${variant_name}") {
            vagrant("/vagrant/ci-scripts/do_repo_init ${manifest}")
        }

        stage("Setup bitbake ${variant_name}") {
            // Setup bitbake environment
            templateconf="${yoctoDir}/sources/meta-pelux/conf/variant/${variant_name}"
            vagrant("/vagrant/cookbook/yocto/initialize-bitbake.sh ${yoctoDir} ${templateconf}")

            // Setup site.conf if not building the master to do a incremental build.
            // The YOCTO_CACHE_URL can be set globally in Manage Jenkins -> Configure System -> Global Properties
            // or for one job as a parameter.
            if (env.YOCTO_CACHE_URL?.trim()) {
                vagrant("sed 's|%CACHEURL%|${env.YOCTO_CACHE_URL}|g' /vagrant/site.conf.in > ${yoctoDir}/build/conf/site.conf")
            }

            // Add other settings that are CI specific to the local.conf
            vagrant("cat /vagrant/local.conf.appendix >> ${yoctoDir}/build/conf/local.conf")
        }

        stage("Fetchall ${variant_name}") {
            // Without cache access, we do want to do fetchall, but only then.
            if (!env.YOCTO_CACHE_URL?.trim()) {
                vagrant("/vagrant/cookbook/yocto/fetch-sources-for-recipes.sh ${yoctoDir} ${bitbake_image}")
            }
        }

        if (variant_name == "qemu-x86-64_nogfx") {
            stage("Setting up local conf for smoke testing and tests export") {
                vagrant("echo '' >> ${yoctoDir}/build/conf/local.conf")
                vagrant("cat /vagrant/tests-support/local.conf.appendix >> ${yoctoDir}/build/conf/local.conf")
            }
        }

        try {

            stage("Bitbake ${variant_name}") {
                vagrant("/vagrant/cookbook/yocto/build-images.sh ${yoctoDir} ${bitbake_image}")
                vagrant("/vagrant/cookbook/yocto/build-images.sh ${yoctoDir} ${bitbake_image}-dev")
            }

            stage("Build SDK ${variant_name}") {
                vagrant("/vagrant/cookbook/yocto/build-sdk.sh ${yoctoDir} ${bitbake_image}")
                vagrant("/vagrant/cookbook/yocto/build-sdk.sh ${yoctoDir} ${bitbake_image}-dev")
            }
        } finally { // Archive cache even if there were errors.

            stage("Archive cache ${variant_name}") {
                // Archive the downloads and sstate when the environment variable was set to true
                // by the Jenkins job.
                if (env.ARCHIVE_CACHE && env.YOCTO_CACHE_ARCHIVE_PATH?.trim()) {
                    vagrant("rsync -trpg ${yoctoDir}/build/downloads/ ${env.YOCTO_CACHE_ARCHIVE_PATH}/downloads/")
                    vagrant("rsync -trpg ${yoctoDir}/build/sstate-cache/ ${env.YOCTO_CACHE_ARCHIVE_PATH}/sstate-cache")
                }
            }
        }

        if (variant_name == "qemu-x86-64_nogfx") {
            try {
                stage("Perform smoke testing") {
                    vagrant("/vagrant/vagrant-cookbook/yocto/runquemu-smoke-test.sh ${yoctoDir} ${bitbake_image}")
                }
            } catch(e) {
               echo "There were failing tests"
            } finally {
                stage("Publish smoke test results") {
                    reports_dir="/vagrant/${archive_directory}/test_reports/${bitbake_image}/"
                    vagrant("mkdir -p ${reports_dir}")
                    vagrant("cp -a ${yoctoDir}/build/TestResults* ${reports_dir}")
                    junit "${archive_directory}/test_reports/${bitbake_image}/TestResults*/*.xml"
                }

                //try {
                //    stage("Feed Image Size Report"){
                //        vagrant("/vagrant/tests-support/plot-helper.sh /vagrant/${archive_directory}/test_reports/${bitbake_image}/TestResults*/TEST-disk_usage.DiskUsageTest*.xml > /vagrant/imageSize.csv")
               //     }

               //     stage("Feed Bootup Time Report"){
               //         vagrant("/vagrant/tests-support/plot-helper.sh /vagrant/${archive_directory}/test_reports/${bitbake_image}/TestResults*/TEST-boot_time.BootTimeTest-*.xml > /vagrant/bootupTime.csv")
               //     }
                //}

                //finally {
                //    stage("image size report"){
                //        plot csvFileName: 'imageSizeReport.csv',
                //                csvSeries: [[
                //                                file: 'imageSize.csv',
                //                                exclusionValues: '',
                //                                displayTableFlag: false,
                //                                inclusionFlag: 'OFF',
                //                                url: '']],
                //                group: 'Performance Graphics',
                //                title: "\"${image_variant}\" Image Size",
                //                style: 'line',
                //                exclZero: false,
                //                keepRecords: false,
                //                logarithmic: false,
                //                numBuilds: '',
                //                useDescr: false,
                //                yaxis: 'KB',
                //                yaxisMaximum: '',
                //                yaxisMinimum: ''
                //    }

                //    stage("bootup time report"){
                //        plot csvFileName: 'bootupTimeReport.csv',
                //                csvSeries: [[
                //                                file: 'bootupTime.csv',
                //                                exclusionValues: '',
                //                                displayTableFlag: false,
                //                                inclusionFlag: 'OFF',
                //                                url: '']],
                //                group: 'Performance Graphics',
                //                title: "\"${image_variant}\" Bootup Time",
                //                style: 'line',
                //                exclZero: false,
                //                keepRecords: false,
                //                logarithmic: false,
                //                numBuilds: '',
                //                useDescr: false,
                //                yaxis: 'Sec.',
                //                yaxisMaximum: '',
                //                yaxisMinimum: ''
                //    }
                //}
            }
        }

        if (env.NIGHTLY_BUILD) {
            stage("Archive artifacts ${variant_name}") {
                String artifactDir = "artifacts_${variant_name}"

                sh "rm -rf ${artifactDir}"
                sh "mkdir ${artifactDir}"

                // Copy images and SDK to the synced directory
                vagrant("/vagrant/ci-scripts/copy_to_archive ${yoctoDir}/build /vagrant/${artifactDir}")

                // And save them in Jenkins
                archiveArtifacts "${artifactDir}/**"
            }
        }

        // Always try to shut down the machine
        // Shutdown the machine
        sh "vagrant destroy -f || true"
    }
}

// Run the different variants in parallel, on different slaves (if possible)
parallel (
    //'intel':        { buildManifest("intel",             "core-image-pelux-minimal") },
    //'intel-qtauto': { buildManifest("intel-qtauto",      "core-image-pelux-qtauto-neptune") },
    //'rpi':          { buildManifest("rpi",               "core-image-pelux-minimal") },
    //'rpi-qtauto':   { buildManifest("rpi-qtauto",        "core-image-pelux-qtauto-neptune") },
    'qemu':         { buildManifest("qemu-x86-64_nogfx", "core-image-pelux-minimal" ) }
)
