FROM ubuntu:16.04

LABEL description="PELUX Yocto build environment"

# Enables us to overwrite the user and group ID for the yoctouser. See below
ARG userid=1000
ARG groupid=1000

USER root

# Install dependencies in one command to avoid potential use of previous cache
# like explained here: https://stackoverflow.com/a/37727984
RUN apt-get update && \
    apt-get install -y \
        build-essential \
        chrpath \
        coreutils \
        cpio \
        curl \
        cvs \
        debianutils \
        diffstat \
        g++-multilib \
        gawk \
        gcc-multilib \
        git-core \
        graphviz \
        help2man \
        iptables \
        iputils-ping \
        libegl1-mesa \
        libfdt1 \
        libsdl1.2-dev \
        libxml2-utils \
        locales \
        m4 \
        openssh-server \
        python \
        python-pysqlite2 \
        python3 \
        python3-git \
        python3-jinja2 \
        python3-pexpect \
        python3-pip \
        qemu-user \
        repo \
        rsync \
        screen \
        socat \
        subversion \
        sudo \
        sysstat \
        texinfo \
        tmux \
        unzip \
        wget \
        xz-utils

RUN apt-get clean

# For Yocto bitbake -c testimage XML reporting
RUN pip3 install unittest-xml-reporting

# For git-lfs
# The downloaded script is needed since git-lfs is not available per default for Ubuntu 16.04
RUN curl -s https://packagecloud.io/install/repositories/github/git-lfs/script.deb.sh | sudo bash && sudo apt-get install -y git-lfs

# Remove all apt lists to avoid build caching
RUN rm -rf /var/lib/apt/lists/*

# en_US.utf8 is required by Yocto sanity check
RUN /usr/sbin/locale-gen en_US.UTF-8
RUN localedef -i en_US -c -f UTF-8 -A /usr/share/locale/locale.alias en_US.UTF-8
RUN echo 'export LC_ALL="en_US.UTF-8"' >> /etc/profile
ENV LANG en_US.utf8

RUN useradd -U -m yoctouser

# Make sure the user/groupID matches the UID/GID given to Docker. This is so that mounted
# dirs will get the correct permissions
RUN usermod --uid $userid yoctouser
RUN groupmod --gid $groupid yoctouser
RUN echo 'yoctouser:yoctouser' | chpasswd
RUN echo 'yoctouser ALL=(ALL) NOPASSWD:SETENV: ALL' > /etc/sudoers.d/yoctouser

USER yoctouser
WORKDIR /home/yoctouser
CMD /bin/bash

# Set up git and repo
ADD --chown=yoctouser:yoctouser cookbook /tmp/cookbook/
