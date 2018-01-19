FROM crops/yocto:ubuntu-16.04-base

LABEL description="PELUX Yocto build environment"
LABEL maintainer="tobias.olausson@pelagicore.com"

# Enables us to overwrite the user ID for the yoctouser. See below
ARG userid=1000

USER root

# Install dependencies in one command to avoid potential use of previous cache
# like explained here: https://stackoverflow.com/a/37727984
RUN apt-get update && apt-get install -y \
        openssh-server \
        inetutils-ping \
        iptables \
        cvs \
        subversion \
        coreutils \
        python3-pip \
        libfdt1 \
        python-pysqlite2 \
        help2man \
        libxml2-utils \
        libsdl1.2-dev \
        graphviz \
        qemu-user \
        g++-multilib \
        curl

# For Yocto bitbake -c testimage XML reporting
RUN pip3 install unittest-xml-reporting

# Remove all apt lists to avoid build caching
RUN rm -rf /var/lib/apt/lists/*

# en_US.utf8 is required by Yocto sanity check
RUN localedef -i en_US -c -f UTF-8 -A /usr/share/locale/locale.alias en_US.UTF-8
RUN echo 'export LC_ALL="en_US.UTF-8"' >> /etc/profile
ENV LANG en_US.utf8

# Make sure the userID matches the UID given to Docker. This is so that mounted
# dirs will get the correct permissions
RUN usermod --uid $userid yoctouser
RUN echo 'yoctouser:yoctouser' | chpasswd
RUN echo 'yoctouser ALL=(ALL) NOPASSWD:SETENV: ALL' > /etc/sudoers.d/yoctouser

# Set up git and repo
USER yoctouser
ADD --chown=yoctouser:yoctouser vagrant-cookbook /tmp/vagrant-cookbook/
# Set up git config --global stuff
RUN /tmp/vagrant-cookbook/system-config/vagrant-ssh-user.sh
# Download and put repo in PATH
RUN /tmp/vagrant-cookbook/yocto/initialize-repo-tool.sh
ENV PATH /home/yoctouser/bin:$PATH

# SSH settings
USER root
RUN mkdir /var/run/sshd
EXPOSE 22
CMD  ["/usr/sbin/sshd", "-D"]
