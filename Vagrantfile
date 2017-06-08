# -*- mode: ruby -*-
# vi: set ft=ruby :

# Path to extra disk
disk_name = 'yocto_disk.vdi'
disk_size_gb = 200

# System resources
ram = 16 #GB
cpus = 6

Vagrant.configure(2) do |config|

    # Prefer docker over virtualbox since it is listed first
    # meaning that docker should run if no '--provider' is supplied
    # when calling vagrant up
    config.vm.provider "docker" do |d, configOverride|
        d.build_dir = "."
        d.pull = true
        d.has_ssh = true

        # Overrides for 'config' unique for docker
        configOverride.ssh.username = "vagrant"
        configOverride.ssh.password = "vagrant"
    end

    config.vm.provider "virtualbox" do |vb, configOverride|
        vb.memory = ram * 1024
        vb.cpus = cpus

        # Overrides for 'configs' unique for virtualbox
        configOverride.vm.box = "debian/jessie64"
    end


    # On some hosts, the network stack needs to be kicked alive
    config.vm.provision "shell", privileged: false, inline: <<-SHELL
        ping google.com &> /dev/null &
    SHELL

    # Install dependencies for BitBake
    config.vm.provision "shell", path: "vagrant-cookbook/deps/yocto.sh"

    # Configure username and password in git
    config.vm.provision "shell", privileged: false, path: "vagrant-cookbook/system-config/vagrant-ssh-user.sh"

    # Configure username and password in git
    config.vm.provision "shell", privileged: false, path: "vagrant-cookbook/yocto/initialize-repo-tool.sh"

end

