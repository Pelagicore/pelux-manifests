PELUX baseline manifests
=========================
This manifest repository is used for building the PELUX baseline for various
hardware targets using the Yocto project.

Maintained at https://github.com/pelagicore/pelux-manifests

Full documentation of PELUX is available at [the PELUX Software
Factory](http://pelux.io/software-factory/) Please refer to the Software Factory
documentation for how to build an image, or consult the Jenkinsfile, which is
what we use in our CI to build.

Targets
-------
Below is a description of the currently supported hardware targets.

### PELUX Intel
Reference instance for the Intel i7 x86 platform using [Yocto's
BSP](https://www.yoctoproject.org/downloads/bsps/pyro23/intel-corei7-64)

* [Intel NUC](https://en.wikipedia.org/wiki/Next_Unit_of_Computing)
* [Automotive Reference Platform](https://www.youtube.com/watch?v=bPsZKolovQM)
* Minnowboard Max, Turbot

### PELUX Raspberry Pi 3
Reference instance for Raspberry Pi 3. Currently targets 32-bit mode.

Branching
---------
This repository will follow yocto releases. Whenever a new yocto
release has been released, a new branch with the same name will be created
from the master branch.

All feature growth should happen first on the master branch, but will also be
cherry picked back to the latest yocto release branch. Security and bug fixes
will be evaluated case by case and backported as necessary. The ambition is to
actively maintain the two latest releases and/or one year old releases in
this fashion.

License and Copyright
---------------------
Copyright (C) 2015-2017 Pelagicore AB
Copyright (C) 2017-2018 Luxoft Sweden AB

All metadata is MIT licensed unless otherwise stated. Source code included
in tree for individual recipes is under the LICENSE stated in the associated
recipe (.bb file) unless otherwise stated.

License information for any other files is either explicitly stated
or defaults to MIT license.

