#!/usr/bin/env bash

set -e

# 0 check
## check input parameters
if [ $# -ne 2 ]; then
    echo "usage: ./run_trias.sh GITHUB_USER_NAME GITHUB_PASSWORD"
    echo
    echo "As it will clone some repositories from github."
    exit -1
else
    USER_NAME=$1
    PASSWD=$2
fi

## check if the user is root
if [ "$USER" != "root" ]; then
    echo "This script should be run by root"
    exit -1
fi

## check ubuntu version
cat /etc/*release  | grep "Ubuntu 14.04.5"
if [ $? -ne 0 ]; then
    echo "The OS should be 'Ubuntu 14.04.5'"
    exit -1
fi

# 1 download trias core
## install git
apt-get update
apt-get install -y git

## download core
cd /opt/
git clone https://${USER_NAME}:${PASSWD}@github.com/trias-lab/core.git --depth 1

## check commit version
cd core
COMMIT=$(git log | head -n 1 | awk '{print $2}')
if [ "$COMMIT" != "d408c094fc8ac4a3b2d4e01e0e6c7fc654e9f437" ]; then
    echo "The repository 'core' has been updated yet. Please check it before you run this script"
    exit -1
fi
cd ..

# 2 change apt source and install some packages
cp -av /etc/apt/sources.list /etc/apt/sources.list_backup
cp /opt/core/deploy/file/sources.list /etc/apt/sources.list
apt-get update
apt-get install -y --force-yes m4 build-essential devhelp libglib2.0-doc libgtk2.0-doc glade \
libglade2-dev libgtk2.0* openssl libssl-dev libtspi-dev unzip libzmq-dev pkg-config libmysqlclient-dev

# 3 install go-1.11
cd /opt/
wget --quiet https://dl.google.com/go/go1.11.linux-amd64.tar.gz
tar -C /usr/local -xzf go1.11.linux-amd64.tar.gz
mkdir /opt/go
echo "export GOPATH=/opt/go"          >> /etc/profile
echo "export GOROOT=/usr/local/go"    >> /etc/profile
echo 'export PATH=$GOROOT/bin/:$PATH' >> /etc/profile
source /etc/profile
mkdir -p /opt/go/src
mkdir -p /opt/go/bin
mkdir -p /opt/go/pkg

# 4 install pip3 and modules
cd /opt/
apt-get install -y --force-yes python3-pip

pip3 install --no-index --find-links=http://140.143.184.36/packs/pypi -r /opt/core/deploy/file/python/requirements.txt
pip3 install --no-index --find-links=http://140.143.184.36/packs/pypi --upgrade setuptools
pip3 install --no-index --find-links=http://140.143.184.36/packs/pypi -r /opt/core/deploy/file/requirements.txt

# 5 install zmq
mkdir -p /opt/go/src/github.com/pebbe
cd /opt/go/src/github.com/pebbe
git clone https://github.com/pebbe/zmq4.git
sh /opt/core/deploy/file/zeromq_install.sh

# 6 download & install tmware
mkdir -p /opt/go/src/github.com/tendermint/
cd /opt/go/src/github.com/tendermint/
git clone https://${USER_NAME}:${PASSWD}@github.com/trias-lab/tmware.git tendermint --depth 1

cd /opt/go/
go build -o bin/tendermint src/github.com/tendermint/tendermint/cmd/tendermint/main.go

# 7 download & install triascode_app
mkdir -p /opt/go/src/trias/
cd /opt/go/src/trias/
git clone https://${USER_NAME}:${PASSWD}@github.com/trias-lab/trias-application.git --depth 1

cd /opt/go/
go build -o bin/triascode_app src/trias/trias-application/cmd/triascode_app.go

# 8 download & install trias_accs
cd /opt/go/src/
git clone https://${USER_NAME}:${PASSWD}@github.com/trias-lab/tribc.git
cd /opt/go/src/tribc
git checkout -b SameA origin/SameA

cd /opt/go
go build -o bin/trias_accs src/tribc/trias_accs.go

# 9 install blackbox & blackbox_agent
mkdir -p /opt/python
cd /opt/python
cp /opt/core . -r
cd /opt/python/core/TCserver/worker
cp -av Blackbox_global.py blackbox_global.py
rm -rf build/
apt-get install -y --force-yes libatlas-base-dev
pyinstaller --clean -F blackbox_main.py
mv dist/blackbox_main dist/blackbox

cd /opt/python/core/TCserver/worker/blackbox_agent
pyinstaller --clean -p /opt/python/core/TCserver/worker -F blackbox_agent.py

# 10 download attestation
cd /opt/python
git clone https://${USER_NAME}:${PASSWD}@github.com/trias-lab/attestation

# 11 download txmodule
cd /opt/python
git clone https://${USER_NAME}:${PASSWD}@github.com/trias-lab/txmodule.git

# 12 modify grub to support IMA
sed -i "/linux\t/s/$/& ima_tcb ima_template=\"ima\" ima_hash=\"sha1\"/g" /boot/grub/grub.cfg

# 13 install tpm-emulator-master
cp -R /opt/core/deploy/file/tpm/tpm-emulator-master /
cd /tpm-emulator-master
sh /tpm-emulator-master/install.sh

# 14 PREPARE FOR TAKEOFF...
# add user 'ubuntu' and 'verfiy', for special usage.
useradd -m -s /bin/bash ubuntu
useradd -m -u 1011 -g root verfiy

## copy 'tendermint', 'triascode_app' and 'trias_accs'
cp /opt/go/bin/triascode_app /usr/local/bin/ && chown ubuntu:ubuntu /usr/local/bin/triascode_app && chmod 755 /usr/local/bin/triascode_app
cp /opt/go/bin/tendermint    /usr/local/bin/ && chown ubuntu:ubuntu /usr/local/bin/tendermint    && chmod 755 /usr/local/bin/tendermint
cp /opt/go/bin/trias_accs    /usr/local/bin/ && chown ubuntu:ubuntu /usr/local/bin/trias_accs    && chmod 755 /usr/local/bin/trias_accs

mkdir -p /trias/log /trias/.ethermint/keystore /trias/.ethermint/tendermint
chown -R ubuntu:ubuntu /trias

## copy 'blackbox' and 'blackbox_agent'
cp -R /opt/core/deploy/file/8lab /
mkdir /8lab/log /8lab/conf /8lab/config /8lab/attestation
chown -R verfiy:root /8lab/attestation/
chown verfiy:root /8lab/log/
mkdir /var/log/8lab/
chown -R verfiy:root /var/log/8lab/

cp -av /opt/python/core/TCserver/worker/dist/blackbox /8lab/
cp -av /opt/python/core/TCserver/worker/blackbox_agent/dist/blackbox_agent /8lab/
cp -R /opt/core/deploy/file/configure.json /8lab/conf/
## change ip address
IP=$(ip addr | grep 'state UP' -A2 | tail -n1 | awk '{print $2}' | cut -f1  -d'/')
sed -i -r "s/(\"SeverIP\": \")[^\"]*/\1$IP/"      /8lab/conf/configure.json
sed -i -r "s/(\"RestIP\": \")[^\"]*/\1$IP/"       /8lab/conf/configure.json
sed -i -r "s/(\"TrueClientIP\": \")[^\"]*/\1$IP/" /8lab/conf/configure.json

## copy attestation
cp -R /opt/python/attestation /
chown -R verfiy:root /attestation

## copy txmodule
cp -R /opt/python/txmodule /
chown -R verfiy:root /txmodule

## copy the startup scripts:
##  'Trias' starts up 'tendermint' and 'triascode_app',
##  'start-tpmd' starts up tpmd and tcsd,
##  'BlackBoxClient' starts up 'blackbox', 'blackbox_agent' and 'attestation'.
cp /opt/core/deploy/file/Trias          /etc/init.d/ && chmod 775 /etc/init.d/Trias
cp /opt/core/deploy/file/start-tpmd     /etc/init.d/ && chmod 775 /etc/init.d/start-tpmd
cp /opt/core/deploy/file/BlackBoxClient /etc/init.d/ && chmod 775 /etc/init.d/BlackBoxClient

# 15 Startup services. After inputting the three commands below, the services can startup automaticlly after reboot.
update-rc.d BlackBoxClient defaults
update-rc.d start-tpmd defaults
update-rc.d Trias defaults

# 16 Flying...
/etc/init.d/start-tpmd     start
/etc/init.d/Trias          start
/etc/init.d/BlackBoxClient start

# 17 Testing...
# You can test the services as following..
#
#    curl -S "http://$IP:46657/tri_bc_tx_commit?tx=\"789\""
#
#    get the transaction hash from the outputs
#    HASH=.....
#
#    curl -S "http://localhost:46657/tri_block_tx?hash=0x$HASH"





