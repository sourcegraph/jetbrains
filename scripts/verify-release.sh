#!/usr/bin/env bash
set -eux

./gradlew clean buildCodeSearchAssets buildPluginAndAssertAgentBinariesExist runPluginVerifier
