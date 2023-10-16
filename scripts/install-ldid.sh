#!/usr/bin/env bash
set -eux

# Check if ldid is installed
if command -v ldid &>/dev/null; then
  echo "ldid is already installed."
  exit 0
fi

curl -Lo ldid.zip https://github.com/xerub/ldid/archive/refs/heads/master.zip
unzip ldid.zip
cd ldid-master
./make.sh
cp ldid2 /usr/local/bin/



