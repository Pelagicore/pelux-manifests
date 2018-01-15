FROM crops/yocto:ubuntu-16.04-base

ARG userid=1000

USER root

RUN mkdir /var/run/sshd

# Install dependencies in one command to avoid potential use of previous cache
# like explained here: https://stackoverflow.com/a/37727984
RUN apt-get update \
    && apt-get install -y openssh-server inetutils-ping iptables cvs subversion coreutils python3-pip libfdt1 python-pysqlite2 help2man libxml2-utils libsdl1.2-dev graphviz qemu-user g++-multilib curl

# For Yocto bitbake -c testimage XML reporting
RUN pip3 install unittest-xml-reporting

RUN rm -rf /var/lib/apt/lists/*
    
# en_US.utf8 is required by Yocto sanity check
RUN localedef -i en_US -c -f UTF-8 -A /usr/share/locale/locale.alias en_US.UTF-8
ENV LANG en_US.utf8

RUN usermod --uid $userid yoctouser
RUN echo 'yoctouser:yoctouser' | chpasswd
RUN echo 'yoctouser ALL=(ALL) NOPASSWD:SETENV: ALL' > /etc/sudoers.d/yoctouser

EXPOSE 22
CMD  ["/usr/sbin/sshd", "-D"]
