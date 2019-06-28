#!/usr/bin/env bash

programname=$0
function usage {
    echo "usage: $programname -i image -v variant [-b branch]"
    echo "   -i image    specify image name would you like to build"
    echo "   -v variant  specify variant of the target platform"
    echo "   -b branch   specify branch name which you would like to use for sources (optional)"
    echo "   -h          display help"


}
BRANCH='master'
while getopts hi:v:b: option; do
    case "${option}" in
        i) IMAGE=${OPTARG};;
        v) VARIANT=${OPTARG};;
        b) BRANCH=$OPTARG;;
        h) usage
        exit 0;;
    esac
done

repo init -u https://github.com/Pelagicore/pelux-manifests.git -b $BRANCH;
repo sync;
TEMPLATECONF=`pwd`/sources/meta-pelux/conf/variant/$VARIANT;
source sources/poky/oe-init-build-env build;
bitbake $IMAGE;
