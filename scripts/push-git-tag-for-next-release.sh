#!/usr/bin/env bash

set -eux

SCRIPT_DIR="$(dirname "$0")"
SCRIPT_DIR="$(readlink -f $SCRIPT_DIR)"
NEXT_VERSION="$(bash "$SCRIPT_DIR/next-release.sh")"
bash "$SCRIPT_DIR/verify-release.sh
TAG="v$NEXT_VERSION"
echo $TAG
git tag -fa "$TAG" -m $TAG && git push -f origin $TAG