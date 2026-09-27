#!/usr/bin/env bash

set -eu

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
SERVER_ROOT=$( dirname "${SCRIPT_DIR}" )

cd "${SERVER_ROOT}"

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
jbang jbang-fmt@jbangdev @config/jbang-fmt.args "$@"
jbang scripts/src/ClosingBraceFix.java "$@"
