#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
PROJECT_ROOT=$( dirname "${SCRIPT_DIR}" )

usage() {
  echo "Usage: $(basename "$0") <source-connection-string> [output-dir]" >&2
  echo "  e.g.: $(basename "$0") 'postgresql://user@prod-host:5432/openvsx' db/dump" >&2
  exit 1
}

[ $# -ge 1 ] || usage
SOURCE_DB_URL="$1"
# Anchored to the repo root, same as scrub-db-dump.sh/import-db-dump.sh's own db/dump default -
# a relative default here would instead resolve against the caller's cwd, so invoking this script
# by path from somewhere else would write a dump neither of those two scripts would find.
OUT_DIR="${2:-${PROJECT_ROOT}/db/dump}"

command -v psql >/dev/null || { echo "This script needs 'psql' on PATH." >&2; exit 1; }

# The dump holds raw secrets until scrub-db-dump.sh redacts them (personal_access_token.value,
# signature_key_pair.private_key, user_data.eclipse_token) - keep every file/directory this script
# creates readable only by the current user, regardless of the caller's umask.
umask 077

mkdir -p "${OUT_DIR}"
OUT_DIR="$(cd "${OUT_DIR}" && pwd)"

# The same 9 tables, and the same per-table "format text, delimiter ','" layout, import-db-dump.sh
# reloads from db/dump - see that script for how these get loaded back (and scrub-db-dump.sh for
# how the 3 sensitive ones among them get redacted first).
TABLES=(extension extension_version file_resource namespace namespace_membership personal_access_token signature_key_pair user_data extension_version_change)

# Stage next to OUT_DIR (same filesystem, so both renames below are fast, atomic directory
# operations) and only publish once psql has committed. \copy writes its target file directly and
# independently of the transaction it runs in, so writing straight into OUT_DIR could otherwise
# leave it with some freshly-overwritten tables and some left over from a previous run if it failed
# partway through - a mixed, inconsistent directory that still passes import-db-dump.sh's own
# existence checks.
STAGE_DIR=$(mktemp -d "${OUT_DIR}.XXXXXX")
PREVIOUS_DIR=""
trap 'rm -rf "${STAGE_DIR}" "${PREVIOUS_DIR}"' EXIT

{
  echo "BEGIN;"
  # One snapshot for every table below, however long the export takes - concurrent writes on the
  # source never become visible mid-export, so e.g. extension_version and file_resource can't end
  # up looking at different points in time relative to each other.
  echo "SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY;"
  for t in "${TABLES[@]}"; do
    echo "\\copy ${t} to '${STAGE_DIR}/${t}.csv' with (format text, delimiter ',')"
  done
  echo "COMMIT;"
} | psql "${SOURCE_DB_URL}" -v ON_ERROR_STOP=1

# Publish the whole generation as two directory renames rather than moving each table's file into
# OUT_DIR individually - a crash or a failed move partway through a per-file loop could leave
# OUT_DIR with some tables on the new generation and some still on the old one, the very mixed
# state staging above is meant to prevent. Renaming a directory is a single filesystem operation,
# so OUT_DIR can only ever end up fully on the previous generation or fully on this one.
PREVIOUS_DIR="${OUT_DIR}.previous.$$"
mv "${OUT_DIR}" "${PREVIOUS_DIR}"
mv "${STAGE_DIR}" "${OUT_DIR}"

echo "Done. Dump files in ${OUT_DIR}:"
ls -la "${OUT_DIR}"/*.csv
