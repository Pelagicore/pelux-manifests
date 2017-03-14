Getting started
===============
This section describes how to get started using a PELUX baseline build.

The general procedure involves writing the built image to e.g. a USB-stick
or SD-card and booting up a target using that. The following sections are divided
in the steps involved, and have sub-sections where there is a difference
between the hardware targets.

These instructions assume there is an image built according to the
[README.md](README.md), with the following additional assumptions:

* The top level of the build is located in `$HOME/pelux-baseline`
* In that top level directory there is a directory named `build`
* The `pelux-intel.xml` manifest was used
* The `core-image-pelux` image was built

Writing the image
-----------------
Poky provides a convenience script for writing images. Using an unmounted
USB-stick or SD-card, do:

```
$HOME/pelux-baseline/sources/poky/scripts/contrib/mkefidisk.sh <host-device> $HOME/pelux-baseline/build/tmp/deploy/images/intel-corei7-64/core-image-pelux-intel-corei7-64.hddimg <target-device>
```

Where `<host-device>` is the device on the host, e.g. `/dev/sdc`, and `<target-device>` is how the target hardware will see the media, e.g. `/dev/sda`.

Booting on target hardware
--------------------------

### NUC
Plug in the USB-stick and boot the NUC. No other actions should be needed.

Login as user `root`.
