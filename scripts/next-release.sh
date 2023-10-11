#!/usr/bin/env bash
set -eu

if ! command -v gdate &>/dev/null; then
  echo "command not found: gdate"
  echo "The command gdate is required to compute the next version number"
  echo "To fix this problem, run:\n  brew install coreutils"
  exit 1
fi


MAJOR_MINOR=$(gh release list --repo sourcegraph/sourcegraph --limit 1 --exclude-drafts --exclude-pre-releases | awk '{ print $2 }' | tail -n 1 | cut -d. -f1 -f2)
LAST_RELEASE_TIMESTAMP=$(gh release list --repo sourcegraph/sourcegraph --limit 1 --exclude-drafts --exclude-pre-releases | awk '{ print $5 }')

# Current year
MILLIS_START_YEAR="$(gdate -d "$LAST_RELEASE_TIMESTAMP" +%s%3N)"
MILLIS_NOW="$(gdate +%s%3N)"
BUILDNUM_MILLIS="$(($MILLIS_NOW - $MILLIS_START_YEAR))"
MILLIS_IN_ONE_MINUTE=60000
MINUTES_IN_ONE_YEAR=525600 # assuming 365 days
MAX_SEMVER_PATCH_NUMBER=65535 # per Microsoft guidelines
BUILDNUM_MINUTES="$(($BUILDNUM_MILLIS / $MILLIS_IN_ONE_MINUTE))"
BUILDNUM="$(($BUILDNUM_MINUTES * $MAX_SEMVER_PATCH_NUMBER / $MINUTES_IN_ONE_YEAR ))"
echo "$MAJOR_MINOR.$BUILDNUM"