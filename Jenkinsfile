#!/usr/bin/groovy

// Copyright (C) Luxoft Sweden AB 2018

// Run the different variants in parallel, on different slaves (if possible)

// We could do a single "checkout scm" and load the groovy script, but to do
// that one has to allocate a node first, which would stay busy until the end of
// the parallel pipeline

void buildOnYoctoNode(String variant, String image) {
    node("Yocto") {

        checkout scm
        def manifests = load "ci-scripts/yocto.groovy"

        manifests.buildManifest(variant, image, env.SMOKE_TEST == "true")
    }
}

def variantMap = [:]
def variantList = env.VARIANT_LIST.split()

variantList.each {
    def list = it.split(":")
    variantMap["${list[0]}"] = {
        buildOnYoctoNode(list[0], list[1])
    }
}

parallel (variantMap)
