#!/usr/bin/env bash

set -eu

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
SERVER_ROOT=$( dirname "${SCRIPT_DIR}" )

cd "${SERVER_ROOT}"

# jbang-fmt hardcodes an org.eclipse.jdt.core older than the one the spotless path formats with,
# and the two disagree (record headers, `){` vs `) {`), so override it to the same version as
# buildSrc/build.gradle and the //DEPS lines in scripts/src/{AddBracesFix,ClosingBraceFix}.java.
JDT_VERSION=3.46.0

if [ "$#" -eq 0 ]; then
    # no paths given (e.g. a manual full-tree run) - format everything
    set -- src/main/java src/test/java
else
    # pre-commit passes paths relative to the repo root, e.g.
    # "server/src/main/java/org/eclipse/openvsx/Foo.java" - strip the "server/"
    # prefix so they resolve correctly now that we've cd'ed into SERVER_ROOT
    paths=()
    for path in "$@"; do
        paths+=("${path#server/}")
    done
    set -- "${paths[@]}"
fi

jbang scripts/src/ImportSort.java "$@"
jbang scripts/src/AddBracesFix.java "$@"
jbang run --deps "org.eclipse.jdt:org.eclipse.jdt.core:${JDT_VERSION}" \
    jbang-fmt@jbangdev @config/jbang-fmt.args "$@"
jbang scripts/src/ClosingBraceFix.java "$@"
