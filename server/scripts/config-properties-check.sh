#!/usr/bin/env bash

# Checks that every ovsx.* property the server binds is documented in doc/configuration.md, and
# that the reference names no property the server does not bind. The bound set comes from
# scripts/config-properties-report.sh, so there is no second scanner to keep in sync.
#
# Known gaps live in scripts/config-properties-baseline.txt and may only shrink: documenting a
# property means deleting its line there, and this fails until it is deleted.
#
# Usage: config-properties-check.sh

set -eu

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
SERVER_ROOT=$( dirname "${SCRIPT_DIR}" )
REPO_ROOT=$( dirname "${SERVER_ROOT}" )

REFERENCE="${REPO_ROOT}/doc/configuration.md"
BASELINE="${SCRIPT_DIR}/config-properties-baseline.txt"

cd "${SERVER_ROOT}"

WORK=$( mktemp -d )
trap 'rm -rf "${WORK}"' EXIT

jbang scripts/src/ConfigPropertiesReport.java --keys | grep '^ovsx\.' | sort -u > "${WORK}/bound"

# Each property is introduced by a "| Property | `key`" row; a mention anywhere else in the prose
# is a cross-reference, not a definition, and must not count as documenting it.
#
# A block whose Compatibility row says the property was removed is a tombstone, kept so that
# someone upgrading from a config file that still sets it can find out what happened to it. It
# describes a past release rather than this one, so it is not expected to be bound - and if it ever
# is again, it shows up as undocumented, which is the right complaint.
# shellcheck disable=SC2016  # the backticks are markdown, not command substitution
awk '
    /^\| Property +\| `/ {
        if (key != "" && !removed) { print key }
        match($0, /`[^`]+`/)
        key = substr($0, RSTART + 1, RLENGTH - 2)
        removed = 0
        next
    }
    /^\| Compatibility +\|/ && /removed/ { removed = 1 }
    END { if (key != "" && !removed) { print key } }
' "${REFERENCE}" | sort -u > "${WORK}/documented"

baseline() {
    { grep -E "^${1} " "${BASELINE}" || true; } | awk '{ print $2 }' | sort -u
}
baseline undocumented > "${WORK}/baseline-undocumented"
baseline unbound > "${WORK}/baseline-unbound"

comm -23 "${WORK}/bound" "${WORK}/documented" > "${WORK}/undocumented"
comm -13 "${WORK}/bound" "${WORK}/documented" > "${WORK}/unbound"

STATUS=0

complain() {
    local file=$1 heading=$2 remedy=$3
    if [ -s "${file}" ]; then
        echo
        echo "${heading}"
        sed 's/^/  /' "${file}"
        echo "${remedy}"
        STATUS=1
    fi
}

comm -23 "${WORK}/undocumented" "${WORK}/baseline-undocumented" > "${WORK}/new-undocumented"
complain "${WORK}/new-undocumented" \
    "These properties are read by the server but not documented in doc/configuration.md:" \
    "Add a section for each, or add 'undocumented <key>' to scripts/config-properties-baseline.txt."

comm -23 "${WORK}/unbound" "${WORK}/baseline-unbound" > "${WORK}/new-unbound"
complain "${WORK}/new-unbound" \
    "doc/configuration.md documents these, but the server binds nothing by that name:" \
    "Remove or rename them in the reference, or add 'unbound <key>' to scripts/config-properties-baseline.txt."

comm -13 "${WORK}/undocumented" "${WORK}/baseline-undocumented" > "${WORK}/fixed-undocumented"
complain "${WORK}/fixed-undocumented" \
    "These are listed as undocumented in the baseline but are now documented:" \
    "Delete their lines from scripts/config-properties-baseline.txt - it may only shrink."

comm -13 "${WORK}/unbound" "${WORK}/baseline-unbound" > "${WORK}/fixed-unbound"
complain "${WORK}/fixed-unbound" \
    "These are listed as unbound in the baseline but the reference no longer has that problem:" \
    "Delete their lines from scripts/config-properties-baseline.txt - it may only shrink."

if [ "${STATUS}" -eq 0 ]; then
    TOLERATED=$( grep -cE '^(undocumented|unbound) ' "${BASELINE}" || true )
    echo "$( wc -l < "${WORK}/bound" | tr -d ' ' ) properties bound, $( wc -l < "${WORK}/documented" | tr -d ' ' ) documented, ${TOLERATED} gaps tolerated by the baseline."
fi

exit "${STATUS}"
