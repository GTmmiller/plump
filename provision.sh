# Install preliminary resources
apt-get update
apt-get install -y build-essential autoconf libtool pkg-config

# install cmake
wget -q -O cmake-linux.sh https://github.com/Kitware/CMake/releases/download/v3.17.0/cmake-3.17.0-Linux-x86_64.sh
$ sh cmake-linux.sh -- --skip-license
$ rm cmake-linux.sh