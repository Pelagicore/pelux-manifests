# -*- mode: ruby -*-
# vi: set ft=ruby :

# Path to extra disk
disk_name = 'yocto_disk.vdi'
disk_size_gb = 200

# System resources
ram = 16 #GB
cpus = 6

manifest = ENV['MANIFEST']

if (manifest.nil? || manifest == 0)
    abort("MANIFEST must be specified")
end

Vagrant.configure(2) do |config|

    config.vm.box = "debian/jessie64"

    config.vm.provider "virtualbox" do |vb|
        vb.memory = ram * 1024
        vb.cpus = cpus
    end

    # Attach an extra disk to /home/vagrant
    eval (File.read 'vagrant-cookbook/host-system-config/attach-extra-disk.vagrantfile')
    config.vm.provision "shell", args: ["/dev/sdb"], path: "vagrant-cookbook/host-system-config/create-disk.sh"

    # On some hosts, the network stack needs to be kicked alive
    config.vm.provision "shell", inline: <<-SHELL
        ping google.com &> /dev/null &
    SHELL

    # Install dependencies for BitBake
    config.vm.provision "shell", path: "vagrant-cookbook/deps/yocto.sh"

    # Not all networks can handle IPv6, so we disable it for now
    config.vm.provision "shell", path: "vagrant-cookbook/system-config/disable-ipv6.sh"

    # Configure username and password in git
    config.vm.provision "shell", privileged: false, path: "vagrant-cookbook/system-config/vagrant-ssh-user.sh"

    # Configure username and password in git
    config.vm.provision "shell", privileged: false, path: "vagrant-cookbook/yocto/initialize-repo-tool.sh"

    # Initialize Yocto environment
    config.vm.provision "shell", privileged: false, args: [manifest], inline: <<-SHELL
        MANIFEST=$1

        echo "Running repo init with the following settings:"
        echo "manifest=${MANIFEST}\n"

        # Copy the host repo to ensure that no later git commands destroy anything.
        SYNC_DIR="/vagrant"
        COPY_DIR="/tmp/git_repo"
        cp -r ${SYNC_DIR} ${COPY_DIR}

        # Make sure there is a master branch and that it is pointing to the latest commit.
        cd ${COPY_DIR}
        git branch -D master || true
        git checkout -b master
        cd -

        # Clone recipes
        mkdir pelux_yocto
        cd pelux_yocto
        time repo init -u ${COPY_DIR} -m ${MANIFEST} -b master
        time repo sync
    SHELL

    # Fetch the sources
    config.vm.provision "shell",
        args: ["/home/vagrant/pelux_yocto", "core-image-pelux"],
        privileged: false,
        env: {"TEMPLATECONF" => "/home/vagrant/pelux_yocto/sources/meta-pelux-bsp-intel/conf"},
        path: "vagrant-cookbook/yocto/fetch-sources-for-recipes.sh"

    # Build the image
    config.vm.provision "shell",
        args: ["/home/vagrant/pelux_yocto/", "core-image-pelux"],
        privileged: false,
        path: "vagrant-cookbook/yocto/build-images.sh"
end

