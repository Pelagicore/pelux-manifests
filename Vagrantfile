# -*- mode: ruby -*-
# vi: set ft=ruby :

# Path to extra disk
disk_name = 'yocto_disk.vdi'
disk_size_gb = 200

# SSH key to use when cloning with git in guest
vagrant_private_key_file="ssh_private_key_id_rsa"

# System resources
ram = 16 #GB
cpus = 6

manifest = ENV['MANIFEST']

if (manifest.nil? || manifest == 0)
    abort("MANIFEST must be specified")
end

branch = ENV['BRANCH']

if (branch.nil? || branch == 0)
    branch = "master"
end

Vagrant.configure(2) do |config|
    # We don't use the rsynced files
    config.vm.synced_folder '.', '/vagrant', :disabled => true

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

    config.vm.provision "file", source: vagrant_private_key_file, destination: "/home/vagrant/.ssh/id_rsa"

    # Use apt-cacher on main server
    config.vm.provision "shell",
        args: ['10.8.36.16'],
        path: "vagrant-cookbook/system-config/apt-cacher.sh"

    # Install dependencies for BitBake
    config.vm.provision "shell", path: "vagrant-cookbook/deps/yocto.sh"

    # Not all networks can handle IPv6, so we disable it for now
    config.vm.provision "shell", path: "vagrant-cookbook/system-config/disable-ipv6.sh"

    # Set of git access for git.pelagicore.net
    config.vm.provision "shell", privileged: false, path: "vagrant-cookbook/system-config/ssh-keyscan-conf.sh"

    # Configure username and password in git
    config.vm.provision "shell", privileged: false, path: "vagrant-cookbook/system-config/vagrant-ssh-user.sh"

    # Configure username and password in git
    config.vm.provision "shell", privileged: false, path: "vagrant-cookbook/yocto/initialize-repo-tool.sh"

    # Initialize Yocto environment
    config.vm.provision "shell", privileged: false, args: [manifest, branch], inline: <<-SHELL
        MANIFEST=$1
        BRANCH=$2
        export CONFDIR=/home/vagrant/pelux_yocto/build/conf

        # Clone recipes
        mkdir pelux_yocto
        cd pelux_yocto
        time repo init -u ssh://git@git.pelagicore.net/viktor-sjolind/pelux-manifests.git -m $MANIFEST -b $BRANCH
        time repo sync

        # Tweak configs
        cp "$CONFDIR/local.conf.sample" "$CONFDIR/local.conf"
        cp "$CONFDIR/bblayers.conf.sample" "$CONFDIR/bblayers.conf"
        sed -i 's:<Yocto root>:/home/vagrant/pelux_yocto/:' "$CONFDIR/bblayers.conf"
    SHELL

    # Fetch the sources
    config.vm.provision "shell",
        args: ["/home/vagrant/pelux_yocto", "core-image-pelux"],
        privileged: false,
        path: "vagrant-cookbook/yocto/fetch-sources-for-recipes.sh"

    # Build the kernel
    config.vm.provision "shell",
        args: ["/home/vagrant/pelux_yocto/", "core-image-pelux"],
        privileged: false,
        path: "vagrant-cookbook/yocto/build-images.sh"
end

