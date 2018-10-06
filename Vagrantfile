# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure(2) do |config|
    config.vm.provider "docker" do |d, configOverride|
        d.build_dir = "."
        d.has_ssh = true
        d.build_args = ['--build-arg', 'userid=' + `id -u`.strip, '--build-arg', 'groupid=' + `id -g`.strip]
        d.create_args = ['--cap-add=NET_ADMIN', '--device=/dev/net/tun']
        d.create_args = ['--security-opt', 'seccomp:unconfined']

        # Overrides for 'config' unique for docker
        configOverride.ssh.username = "yoctouser"
        configOverride.ssh.password = "yoctouser"
    end

    # If an archive path for the yocto cache is given, we mount it into the vm
    # using the same path as on the host.
    unless ENV['YOCTO_CACHE_ARCHIVE_PATH'].to_s.strip.empty?
        config.vm.synced_folder ENV['YOCTO_CACHE_ARCHIVE_PATH'], ENV['YOCTO_CACHE_ARCHIVE_PATH']
    end
end

