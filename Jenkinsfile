#!/usr/bin/groovy

// Copyright (C) Pelagicore AB 2017

/*
 * For supported variant names, see conf/variants/ in meta-pelux
 */
void buildManifest(String variant_name, String bitbake_image) {

    node("DockerCI") {

        // We are going to need these later
        String homeDir = "/home/yoctouser"
        String yoctoDir = "${homeDir}/pelux_yocto"
        String manifest = "pelux.xml"

        stage("Checkout ${variant_name}") {
            checkout scm
            sh 'git submodule update --init'
        }

        stage("Docker build ${variant_name}") {
            // Build image from local Dockerfile with specified uid.
            String uid = sh(returnStdout: true, script: "id -u").trim()
            image = docker.build("pelux", "--build-arg userid=${uid} .")
        }

        // When starting our container, we want to run as the yoctouser, and if we
        // have a yocto cache set up, we want it mounted.
        String dockerArgs = "-u yoctouser"
        if (env.YOCTO_CACHE_ARCHIVE_PATH?.trim()) {
            String cachePath = env.YOCTO_CACHE_ARCHIVE_PATH.trim()
            dockerArgs += " -v ${cachePath}:${cachePath}"
        }

        // From here on, we run these commands inside the docker container. The
        // default pwd inside is the path to the Jenkins workspace.
        image.inside(dockerArgs) {
            stage("Repo init/sync ${variant_name}") {
                String syncDir = sh(returnStdout: true, script: "pwd").trim()
                sh "cd $homeDir && ${syncDir}/ci-scripts/do_repo_init ${manifest} ${syncDir}"
            }

            stage("oe-init-build-env ${variant_name}") {
                templateconf="${yoctoDir}/sources/meta-pelux/conf/variant/${variant_name}"
                sh "vagrant-cookbook/yocto/initialize-bitbake.sh ${yoctoDir} ${templateconf}"

                // Fixes a bug in bitbake that causes file copy to directories to
                // fail. https://patchwork.openembedded.org/patch/144399/
                sh "patch -d ${yoctoDir}/sources/poky -p1 < ${yoctoDir}/sources/meta-bistro/recipes-temporary-patches/bitbake/0001-bitbake-lib-bb-utils-fix-movefile-copy-to-dir-fallba.patch"

                // Setup site.conf if not building the master to do a incremental build.
                // The YOCTO_CACHE_URL can be set globally in Manage Jenkins -> Configure System -> Global Properties
                // or for one job as a parameter.
                if (env.YOCTO_CACHE_URL?.trim()) {
                    sh "sed 's|%CACHEURL%|${env.YOCTO_CACHE_URL}|g' site.conf.in > ${yoctoDir}/build/conf/site.conf"
                }
            }

            // Only run the fetchall step if we are building without a Yocto cache
            if (!env.YOCTO_CACHE_URL?.trim()) {
                stage("Fetchall ${variant_name}") {
                    sh "vagrant-cookbook/yocto/fetch-sources-for-recipes.sh ${yoctoDir} ${bitbake_image}"
                }
            }

            stage("Bitbake ${variant_name}") {
                sh "vagrant-cookbook/yocto/build-images.sh ${yoctoDir} ${bitbake_image}"
                sh "vagrant-cookbook/yocto/build-images.sh ${yoctoDir} ${bitbake_image}-dev"
            }

            stage("Build SDK ${variant_name}") {
                sh "vagrant-cookbook/yocto/build-sdk.sh ${yoctoDir} ${bitbake_image}"
                sh "vagrant-cookbook/yocto/build-sdk.sh ${yoctoDir} ${bitbake_image}-dev"
            }

            // Only run the archiving if we have the cache flags set.
            if (env.ARCHIVE_CACHE && env.YOCTO_CACHE_ARCHIVE_PATH?.trim()) {
                stage("Archive cache ${variant_name}") {
                    sh "rsync -trpg ${yoctoDir}/build/downloads/ ${env.YOCTO_CACHE_ARCHIVE_PATH}/downloads/"
                    sh "rsync -trpg ${yoctoDir}/build/sstate-cache/ ${env.YOCTO_CACHE_ARCHIVE_PATH}/sstate-cache"
                }
            }
        }
    }
}

parallel (
    'intel':        { buildManifest("intel",        "core-image-pelux-minimal") },
    'intel-qtauto': { buildManifest("intel-qtauto", "core-image-pelux-qtauto-neptune") },
    'rpi':          { buildManifest("rpi",          "core-image-pelux-minimal") },
    'rpi-qtauto':   { buildManifest("rpi-qtauto",   "core-image-pelux-qtauto-neptune") }
)
