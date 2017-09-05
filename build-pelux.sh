#!/bin/bash

# Ensure you have repo in this dir
# from https://source.android.com/source/downloading#installing-repo

repo-setup ()  
{
    # Create a place to put repo in your path
    mkdir ~/bin
    PATH=~/bin:$PATH

    # Download the Repo tool and ensure that it is executable:
    curl https://storage.googleapis.com/git-repo-downloads/repo > ~/bin/repo
    chmod a+x ~/bin/repo
}

# check that the repo tool exists, if not, set it up
REPOTOOL=$( which repo )
if [ ! -e ${REPOTOOL} ]; then
    repo-setup
fi

# repo init -u https://github.com/Pelagicore/pelux-manifests.git -m pelux-intel.xml -b master
# repo sync

# if [ ! -e ./build]; then
#     mkdir build
# fi

# build ()
# {
#     export TEMPLATECONF=$( pwd )/sources/meta-pelux-bsp-intel/conf/
#     source sources/poky/oe-init-build-env build
#     bitbake core-image-pelux
# }

# # You can (must?) do a repo sync before re-building
# repo sync

# Troubleshooting
# "Please use a locale setting which supports utf-8.
# Python can't change the filesystem locale after loading so we need a utf-8 when python starts or things won't work."
# export LANGUAGE=en_US.UTF-8
# export LANG=en_US.UTF-8
# export LC_ALL=en_US.UTF-8
# dpkg-reconfigure locales


sd-image-create ()
{
    # hic sunt dracones!

    # Create a new sd image based on the name of the image we're building
    dd if=/dev/zero of="pelux-intel.sdimg" bs=1024 count=1M
    sudo losetup -d /dev/loop0
    sudo losetup /dev/loop0 pelux-intel.sdimg
    sudo bash ~/pelux/sources/poky/scripts/contrib/mkefidisk.sh /dev/loop0 core-image-pelux-intel-corei7-64.hddimg /dev/mmcblk2
    sudo losetup -d /dev/loop0

    #
    DEVICE="/dev/sdb" # get this via command line?
    sudo dd if=pelux-intel.sdimg of=${DEVICE} bs=1024
}
