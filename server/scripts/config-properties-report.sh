#!/usr/bin/env bash

# Lists every Spring config property key read via @Value / @ConfigurationProperties in
# src/main/java, with defaults and source locations. See scripts/src/ConfigPropertiesReport.java.

set -eu

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
SERVER_ROOT=$( dirname "${SCRIPT_DIR}" )

cd "${SERVER_ROOT}"

jbang scripts/src/ConfigPropertiesReport.java "$@"
