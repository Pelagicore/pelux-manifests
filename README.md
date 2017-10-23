PELUX baseline manifests
=========================
This manifest repository is used for building the PELUX baseline for various
hardware targets using the Yocto project.

Maintained at https://github.com/pelagicore/pelux-manifests

Full documentation of PELUX is available at http://pelux.io/software-factory/

Building an Image
-----------------

Use `pelux.xml` for building. It will download all layers needed for both Intel
and Raspberry Pi 3.

An image build can be started using a container/virtual machine, see section
"Using vagrant", or using `repo` tool directly, see section "Using Repo tool".
Since the Repo tool approach is not contained it is notably faster than using
Vagrant. Therefore, the Vagrant approach is usually only used in situations
where one does not want to depend on the host system, such as running
continuous integration jobs.

### Available images

There are two different images available: `core-image-pelux-minimal` and
`core-image-pelux-qtauto-neptune`. The latter being a version that includes
NeptuneUI and QtApplicationManager.

### Using vagrant

Please note that we only run this setup in a GNU/Linux system at Pelagicore. It
should still work under Windows or OSX, we haven't tried it.

Dependencies:

* Vagrant
* Docker
* Virtualization enabled in BIOS

Procedure:

1. Clone the pelux-manifests git repository.
2. Start vagrant

    ```bash
    vagrant up
    ```

3. Set variables to be used below

    ```bash
    export bitbake_image="core-image-pelux"
    export yoctoDir="/home/vagrant/pelux_yocto"
    export manifest="pelux-intel.xml"
    ```

4. Do repo init

    ```bash
    vagrant ssh -c "/vagrant/ci-scripts/do_repo_init ${manifest}"
    ```

5. Setup bitbake with correct local.conf and bblayers.conf

    ```bash
    vagrant ssh -c "TEMPLATECONF=${yoctoDir}/sources/meta-pelux-bsp-intel/conf \
        /vagrant/vagrant-cookbook/yocto/fetch-sources-for-recipes.sh \
        ${yoctoDir} \
        ${bitbake_image}"
    ```

6. Bitbake the PELUX image

    ```bash
    vagrant ssh -c "/vagrant/vagrant-cookbook/yocto/build-images.sh \
        ${yoctoDir} \
        ${bitbake_image}"
    ```

7. Move the built images to the host

    ```bash
    vagrant scp :${yoctoDir}/build/tmp/deploy/images ../images
    ```

Don't put them into the source folder because then they will be syncroniced back
into the docker instance into the `/vagrant` directory which might take a
reasonable amount of resources to do.

The container/virtual machine started via vagrant will sync the cloned git
repository and use the manifests contained in it to set up the build
environment. This means that the branch/commit currently checked out will
determine what version is being built. The final step will copy the image
directory containing the built images to the directory on the host where vagrant
was started.

### Using Repo tool

#### NOTE: When using this approach one should not clone this git repository, all git manipulation is handled by the repo tool.

First, install repo tool as instructed at http://source.android.com/source/downloading.html#installing-repo.

Then create a directory for the PELUX build.
```bash
mkdir pelux
cd pelux
```

Instruct repo tool to fetch a manifest using the command `repo init`. In this
context, branch denotes what branch of git repo `pelux-manifests` to use. Then
make repo tool fetch all sources using the command `repo sync`.
```bash
repo init -u https://github.com/Pelagicore/pelux-manifests.git -m pelux.xml -b <branch>
repo sync
```

When `repo sync` has finished fetching the sources, the next step is to create a
'build' directory and set up bitbake.  The TEMPLATECONF environment setting
tells the `oe-init-build-env` script which path to fetch configuration files
from. Note that the example below gets the template configuration for the Intel
BSP (without QtAS). Adapt the path according to your current BSP.

```bash
export TEMPLATECONF=`pwd`/sources/meta-pelux/meta-intel-extras/conf/
source sources/poky/oe-init-build-env build
```

The script will create configs if there are no configs present, a message about
created `conf/local.conf` and `conf/bblayers.conf` files is normal.

Finally, build the desired image. See the variables description above for
information on the different images.
```bash
bitbake <image>
```

Targets
-------
Below is a description of the currently supported hardware targets. For more
information about how to use a built image with the targets, see
[Getting started](getting-started.md).

### PELUX Intel
Reference instance for the Intel i7 x86 platform using [Yocto's
BSP](https://www.yoctoproject.org/downloads/bsps/pyro23/intel-corei7-64)

* [Intel NUC](https://en.wikipedia.org/wiki/Next_Unit_of_Computing)
* Minnowboard Max, Turbot

### PELUX Raspberry Pi 3
Reference instance for Raspberry Pi 3 (coming soon)

Branching
---------
This repository will follow yocto releases. Whenever a new yocto
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

