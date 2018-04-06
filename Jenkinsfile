#!/usr/bin/groovy

// Copyright (C) Luxoft Sweden AB 2018

def manifests = load "ci-scripts/yocto.groovy"

// Run the different variants in parallel, on different slaves (if possible)
parallel (
    'intel': {
        node("Yocto") {
            manifests.buildManifest("intel", "core-image-pelux-minimal")
        }
    },

    'intel-qtauto': {
        node("Yocto") {
            manifests.buildManifest("intel-qtauto", "core-image-pelux-qtauto-neptune")
        }
    },

    'rpi': {
        node("Yocto") {
            manifests.buildManifest("rpi", "core-image-pelux-minimal")
        }
    },

    'rpi-qtauto': {
        node("Yocto") {
            manifests.buildManifest("rpi-qtauto", "core-image-pelux-qtauto-neptune")
        }
    }
)
