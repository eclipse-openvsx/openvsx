#!/usr/bin/env bash

# Checks the versions driving the Java formatter - org.eclipse.jdt.core and spotless-lib - which
# must agree across the Gradle and jbang paths or the two format the same source differently. See
# scripts/src/FormatterVersionCheck.java for the actual check; it discovers the declaration sites
# from the files themselves, so there is nothing to keep in sync here.
#
# Usage: formatter-version-check.sh

set -eu

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
SERVER_ROOT=$( dirname "${SCRIPT_DIR}" )

cd "${SERVER_ROOT}"

jbang scripts/src/FormatterVersionCheck.java "$@"
