#!/usr/bin/env bash

programname=$0
function usage {
    echo "usage: $programname -i image -v variant [-b branch]"
    echo "   -i image    specify image name would you like to build"
    echo "   -v variant  specify variant of the target platform"
    echo "   -b branch   specify branch name which you would like to use for sources (optional)"
    echo "   -l layer    specify layer name you want to build (optional)"
    echo "   -h          display help"


}
BRANCH='master'
while getopts hi:v:b:l: option; do
    case "${option}" in
        i) IMAGE=${OPTARG};;
        v) VARIANT=${OPTARG};;
        b) BRANCH=$OPTARG;;
        l) LAYER=${OPTARG};;
        h) usage
        exit 0;;
    esac
done

if [[ $# -eq 0 ]] ; then
    echo "variant and image are mandatory arguments"
    usage
    exit 1
fi

repo init -u https://github.com/Pelagicore/pelux-manifests.git -b $BRANCH;
repo sync;

if [ $LAYER ] ; then
    echo ""
    echo "Replacing existing meta layer with custom one"
    echo ""
    rm -rf sources/${LAYER};
    mv ${LAYER} sources/${LAYER};
fi 

TEMPLATECONF=`pwd`/sources/meta-pelux/conf/variant/$VARIANT;
source sources/poky/oe-init-build-env build;
bitbake $IMAGE;
