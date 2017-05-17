PELUX baseline manifests
=========================
This is the manifest repository for building the PELUX baseline for various hardware targets using Yocto

Maintained at https://github.com/pelagicore/pelux-manifests

Building an Image
-----------------

The following manifests can be used for a build:

* `pelux-intel.xml` - For building the `core-image-pelux` image for Intel
* `pelux-intel-qt.xml` - For building the `core-image-pelux-qt` image, which is the baseline with QtAS
* `pelux-rpi.xml` - For building the `core-image-pelux`image for Raspberry Pi 3

An image build can be started using a virtual machine, see section "Using vagrant", or using `repo` tool
directly, see section "Using Repo tool".

Variables:

* Manifest, refers to what `<manifest-name>.xml` file you want to use, for example `pelux-intel.xml`. Each hardware platform targeted by the PELUX reference has its own manifest describing what other git repositories are needed for the build.
* Image, refers to what version of PELUX that should be built. Currently there are two versions: `core-image-pelux` and `core-image-pelux-qt`. The latter being a version that includes NeptuneUI and QtApplicationManager.

### Using vagrant

Dependencies:

* Vagrant
* Virtualbox
* virtualization enabled in bios

Procedure:

1. Clone the pelux-manifests git repository.
2. Start vagrant
```bash
MANIFEST=<manifest> BITBAKE_IMAGE=<image> vagrant up
```

The virtual machine started via vagrant will sync the cloned git repository and use the manifests contained in it
to set up the build environment. This means that the branch/commit currently checked out will determine what version
is being built.

### Using Repo tool

#### NOTE: Using this approach one should not clone this git repository, all git manipulation is handled by the repo tool.

First, install repo tool as instructed at http://source.android.com/source/downloading.html#installing-repo.

Then create a directory for the PELUX build.
```bash
mkdir pelux
cd pelux
```

Instruct repo tool to fetch a manifest using the command `repo init`. In this context, branch denotes what
branch of git repo `pelux-manifests` to use. Then make repo tool fetch all sources using the command `repo sync`.
```bash
repo init -u https://github.com/Pelagicore/pelux-manifests.git -m <manifest> -b <branch>
repo sync
```

When done fetching the sources, create a build directory and set up bitbake. TEMPLATECONF tells the
`oe-init-build-env` script which path to fetch configuration samples from. Note that the example below
get the template configuration for the Intel BSP, adapt the path according to your current BSP.
```bash
TEMPLATECONF=`pwd`/sources/meta-pelux-bsp-intel/conf/ source sources/poky/oe-init-build-env build
```

The script will create configs if there are no configs present, a message about created `conf/local.conf`
and `conf/bblayers.conf` files is normal.


Finally, build the desired image. See the variables description above for information on the different images.
```bash
bitbake <image>
```

Targets
-------
Below is a description of the currently supported hardware targets. For more information about
how to use a built image with the targets, see [Getting started](getting-started.md).

### Pelux Intel
Reference instance for the Intel i7 x86 platform. Examples of boards using this architecture include:

* Intel NUC
* Minnowboard

### Pelux Raspberry Pi
Reference instance for Raspberry Pi 3

Branching
---------
This repository will follow the yocto release system. Whenever a new yocto
release has been released, a new branch with the same name will be created
from the master branch.
All feature growth should happen first on the master branch, but will also be
cherry picked back to the latest yocto release branch. Security and bug fixes
will be evaluated case by case and backported as necessary. The ambition is to
actively maintain the four latest releases and/or two year old releases in
this fashion.

License and Copyright
---------------------
Copyright (C) 2015-2017 Pelagicore AB

All metadata is MIT licensed unless otherwise stated. Source code included
in tree for individual recipes is under the LICENSE stated in the associated
recipe (.bb file) unless otherwise stated.

License information for any other files is either explicitly stated
or defaults to MIT license.

