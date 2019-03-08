# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure(2) do |config|
    config.vm.provider "docker" do |d, configOverride|
        d.build_dir = "."
        d.has_ssh = true
        d.build_args = ['--build-arg', 'userid=' + `id -u`.strip, '--build-arg', 'groupid=' + `id -g`.strip]
        d.create_args = ['--cap-add=NET_ADMIN', '--device=/dev/net/tun']

        # Overrides for 'config' unique for docker
        configOverride.ssh.username = "yoctouser"
        configOverride.ssh.password = "yoctouser"
    end

    # If an archive path for the yocto cache is given, we mount it into the vm
    # using the same path as on the host.
    unless ENV['YOCTO_CACHE_URL'].to_s.strip.empty?
      config.vm.synced_folder ENV['YOCTO_CACHE_URL'], ENV['YOCTO_CACHE_URL']
    else
      config.vm.synced_folder "/var/yocto-cache", "/var/yocto-cache"
    end
    config.ssh.forward_x11 = true
end
