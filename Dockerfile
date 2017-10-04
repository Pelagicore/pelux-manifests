FROM ubuntu:16.04

ARG userid=1000

RUN mkdir /var/run/sshd
RUN apt-get update
RUN apt-get install -y openssh-server sudo

# Required by bitbake
RUN apt-get install -y cpio iputils-ping

# en_US.utf8 is required by Yocto sanity check
RUN apt-get install -y locales && rm -rf /var/lib/apt/lists/* \
    && localedef -i en_US -c -f UTF-8 -A /usr/share/locale/locale.alias en_US.UTF-8
ENV LANG en_US.utf8

RUN useradd --uid $userid vagrant --create-home --user-group --groups sudo
RUN echo 'vagrant:vagrant' | chpasswd

RUN echo 'vagrant ALL=(ALL) NOPASSWD:SETENV: ALL' > /etc/sudoers.d/vagrant

EXPOSE 22
CMD  ["/usr/sbin/sshd", "-D"]

