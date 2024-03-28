#!/usr/bin/env bash
# This script implements the time-based version scheme from RFC 795
# Simplified: versions should be MAJOR.MINOR.PATCH where
# - MAJOR.MINOR: Latest Sourcegraph quarterly release
# - PATCH: time-based number from simplified formula (MINUTES_SINCE_LAST_RELEASE / MINUTES_IN_ONE_YEAR * 65535)
# The scheme gives generates a unique version number every 10 minutes.
# https://docs.google.com/document/d/11cw-7dAp93JmasITNSNCtx31xrQsNB1L2OoxVE6zrTc/edit#bookmark=id.ufwe0bqp83z1
set -eu

# Check the number of arguments
if [ "$#" -ne 1 ]; then
  echo "Usage: $0 [--major | --minor | --patch]"
  exit 1
fi

LAST_MAJOR_MINOR_ZERO_RELEASE=$(gh release list --repo sourcegraph/jetbrains --limit 20 --exclude-drafts | sed 's/Latest//' | sed 's/Pre-release//' | awk '$2 ~ /v[0-9]+\.[0-9]+\.[0-9]+$/ { print $2, $3; exit }')
MAJOR=$(echo $LAST_MAJOR_MINOR_ZERO_RELEASE | awk '{ print $1 }' | sed 's/v//' | cut -d. -f1)
MINOR=$(echo $LAST_MAJOR_MINOR_ZERO_RELEASE | awk '{ print $1 }' | sed 's/v//' | cut -d. -f2)
PATCH=$(echo $LAST_MAJOR_MINOR_ZERO_RELEASE | awk '{ print $1 }' | sed 's/v//' | cut -d. -f3)

NEXT_RELEASE_ARG="$1"
# Check the argument and take appropriate action
if [ "$NEXT_RELEASE_ARG" == "--major" ]; then
  MAJOR=$(($MAJOR+1))
  echo "$MAJOR.0.0"
elif [ "$NEXT_RELEASE_ARG" == "--minor" ]; then
  MINOR=$((MINOR+1))
  echo "$MAJOR.$MINOR.0"
elif [ "$NEXT_RELEASE_ARG" == "--patch" ]; then
  PATCH=$(($PATCH+1))
  echo "$MAJOR.$MINOR.$PATCH"
else
  echo "Invalid argument. Usage: $0 [--major | --minor | --patch]"
  exit 1
fi
