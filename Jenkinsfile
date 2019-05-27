#!/usr/bin/groovy

// Copyright (C) Luxoft Sweden AB 2018

// Run the different variants in parallel, on different slaves (if possible)

// We could do a single "checkout scm" and load the groovy script, but to do
// that one has to allocate a node first, which would stay busy until the end of
// the parallel pipeline

void buildOnYoctoNode(String variant, String image) {
    node("Yocto") {

        checkout scm
        def customImage = docker.image("pelux/pelux-yocto:yoctouser")
        customImage.inside('-v $WORKSPACE:/workspace -v /var/yocto-cache:/var/yocto-cache --cap-add=NET_ADMIN --device=/dev/net/tun') {
            def manifests = load "ci-scripts/yocto2.groovy"
            manifests.buildManifest(variant, image)
        }
    }
}

def variantMap = [:]
def variantList = []

// If VARIANT_LIST is defined in the Jenkins Environment
// then use that, otherwise use the default
// Note: This function will throw a NullPointerException if the
//       variable is not set.
try {
    variantList = env.VARIANT_LIST.split()
    echo "Using the specified variant list for build"
    println "[\"${variantList.join('", "')}\"]"
} catch(e) {
    println("Using the default variant list for build. \nCaught error: " + e)
    variantList = ['intel-qtauto:core-image-pelux-qtauto-neptune-dev',
                    'rpi-qtauto:core-image-pelux-qtauto-neptune-dev']
    println "[\"${variantList.join('", "')}\"]"
}

variantList.each {
    def list = it.split(":")
    variantMap["${list[0]}"] = {
        buildOnYoctoNode(list[0], list[1])
    }
}

parallel (variantMap)
