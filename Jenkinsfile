#!/usr/bin/groovy

// Copyright (C) Luxoft Sweden AB 2018

// Run the different variants in parallel, on different slaves (if possible)

// We could do a single "checkout scm" and load the groovy script, but to do
// that one has to allocate a node first, which would stay busy until the end of
// the parallel pipeline
parallel (
    'intel': {
        node("Yocto") {
            checkout scm
            def manifests = load "ci-scripts/yocto.groovy"
            manifests.buildManifest("intel", "core-image-pelux-minimal")
        }
    },

    'intel-qtauto': {
        node("Yocto") {
            checkout scm
            def manifests = load "ci-scripts/yocto.groovy"
            manifests.buildManifest("intel-qtauto", "core-image-pelux-qtauto-neptune")
        }
    },

    'rpi': {
        node("Yocto") {
            checkout scm
            def manifests = load "ci-scripts/yocto.groovy"
            manifests.buildManifest("rpi", "core-image-pelux-minimal")
        }
    },

    'rpi-qtauto': {
        node("Yocto") {
            checkout scm
            def manifests = load "ci-scripts/yocto.groovy"
            manifests.buildManifest("rpi-qtauto", "core-image-pelux-qtauto-neptune")
        }
    }
)

