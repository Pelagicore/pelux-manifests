PELUX baseline manifests
=========================
This is the manifest repository for building the PELUX reference for various targets using Yocto

Maintained at https://github.com/pelagicore/pelux-manifests

Building an Image
-----------------
Variables:
* Branch, what version of this repository to use. This follows the same principles as the Yocto project, see `Branching` below. 

* Manifest, refers to what `<manifest-name>.xml` file you want to use, for example `pelux-intel.xml`. Each hardware platform targeted by the PELUX reference has its own manifest describing what other git repositories are needed for the build.

### Using vagrant

Dependencies:

* Vagrant
* Virtualbox
* virtualization enabled in bios

On Linux systems:
```bash
MANIFEST=<manifest> BRANCH=<branch> vagrant up
```

### Using Repo tool

```bash
repo init -u https://github.com/Pelagicore/pelux-manifests.git -m <manifest> -b <branch>
repo sync

TEMPLATECONF=../sources/meta-pelux-bsp-intel/conf/ source sources/poky/oe-init-build-env build
bitbake core-image-pelux
```

Targets
-------

### Pelux Intel
Reference instance for the Intel i7 x86 platform. Examples of boards using this architecture include:

* Intel NUC
* Minnowboard

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

