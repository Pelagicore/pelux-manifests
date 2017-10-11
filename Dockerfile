FROM ubuntu:16.04

ARG userid=1000

RUN mkdir /var/run/sshd

# Install dependencies in one command to avoid potential use of previous cache
# like explained here: https://stackoverflow.com/a/37727984
RUN apt-get update \
    && apt-get install -y openssh-server sudo cpio inetutils-ping locales
    
RUN rm -rf /var/lib/apt/lists/*
    
# en_US.utf8 is required by Yocto sanity check
RUN localedef -i en_US -c -f UTF-8 -A /usr/share/locale/locale.alias en_US.UTF-8
ENV LANG en_US.utf8

RUN useradd --uid $userid vagrant --create-home --user-group --groups sudo
RUN echo 'vagrant:vagrant' | chpasswd

RUN echo 'vagrant ALL=(ALL) NOPASSWD:SETENV: ALL' > /etc/sudoers.d/vagrant

EXPOSE 22
CMD  ["/usr/sbin/sshd", "-D"]
