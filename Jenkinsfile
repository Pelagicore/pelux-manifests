#!/usr/bin/groovy

// Copyright (C) Luxoft Sweden AB 2018

// Run the different variants in parallel, on different slaves (if possible)

// We could do a single "checkout scm" and load the groovy script, but to do
// that one has to allocate a node first, which would stay busy until the end of
// the parallel pipeline

void buildOnYoctoNode(String variant, String image, boolean checkSmokeTests = false) {
    node("Yocto") {
        boolean smokeTests = false

        if (checkSmokeTests) {
            smokeTests = env.SMOKE_TEST == "true"
        }

        checkout scm
        def manifests = load "ci-scripts/yocto.groovy"
        manifests.buildManifest(variant, image, smokeTests)
    }
}

void buildOnYoctoNodeNightly(String variant, String image, boolean checkSmokeTests = false) {
    boolean nightlyBuild = env.NIGHTLY_BUILD == "true"

    if (nightlyBuild) {
        buildOnYoctoNode(variant, image, checkSmokeTests)
    } else {
        println("Nothing to do for " + variant)
    }
}


parallel (
    'intel': {
        buildOnYoctoNode("intel", "core-image-pelux-minimal")
    },

    'intel-qtauto': {
        buildOnYoctoNode("intel-qtauto", "core-image-pelux-qtauto-neptune")
    },

    'rpi': {
        buildOnYoctoNode("rpi", "core-image-pelux-minimal")
    },

    'rpi-qtauto': {
        buildOnYoctoNode("rpi-qtauto", "core-image-pelux-qtauto-neptune")
    },

    'qemu': {
        buildOnYoctoNodeNightly("qemu", "core-image-pelux-minimal", true)
    },

    'arp': {
        buildOnYoctoNodeNightly("arp", "core-image-pelux-minimal")
    },

    'arp-qtauto': {
        buildOnYoctoNodeNightly("arp-qtauto", "core-image-pelux-qtauto-neptune")
    }
)
