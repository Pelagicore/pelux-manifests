# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure(2) do |config|
    config.vm.provider "docker" do |d, configOverride|
        d.build_dir = "."
        d.pull = true
        d.has_ssh = true
        d.build_args = ['--build-arg', 'userid=' + `id -u`.strip]

        # Overrides for 'config' unique for docker
        configOverride.ssh.username = "vagrant"
        configOverride.ssh.password = "vagrant"
    end

    # If an archive path for the yocto cache is given, we mount it into the vm
    # using the same path as on the host.
    unless ENV['YOCTO_CACHE_ARCHIVE_PATH'].to_s.strip.empty?
        config.vm.synced_folder ENV['YOCTO_CACHE_ARCHIVE_PATH'], ENV['YOCTO_CACHE_ARCHIVE_PATH']
    end

    # On some hosts, the network stack needs to be kicked alive
    config.vm.provision "shell", privileged: false, inline: <<-SHELL
        ping google.com &> /dev/null &
    SHELL

    # Install dependencies for BitBake
    config.vm.provision "shell", path: "vagrant-cookbook/deps/yocto.sh"

    # Set UTF-8 for BitBake python
    config.vm.provision "shell", privileged: false, inline: <<-SHELL
        echo 'export LC_ALL="en_US.UTF-8"' >> .profile
    SHELL

    # Configure username and password in git
    config.vm.provision "shell", privileged: false, path: "vagrant-cookbook/system-config/vagrant-ssh-user.sh"

    # Configure username and password in git
    config.vm.provision "shell", privileged: false, path: "vagrant-cookbook/yocto/initialize-repo-tool.sh"

end

