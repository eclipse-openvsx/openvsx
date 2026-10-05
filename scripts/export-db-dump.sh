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
# -P resolves any symlink in the path to its real, physical location - the per-file renames below
# land in the directory a symlink points at either way, but staging next to the resolved path
# keeps everything on the same filesystem as that real directory rather than possibly the one the
# symlink itself lives on.
OUT_DIR="$(cd "${OUT_DIR}" && pwd -P)"

# The same 9 tables, and the same per-table "format text, delimiter ','" layout, import-db-dump.sh
# reloads from db/dump - see that script for how these get loaded back (and scrub-db-dump.sh for
# how the 3 sensitive ones among them get redacted first, keeping each original alongside as
# <table>.csv.bak until it's been reviewed).
#
# user_data_permission is deliberately not among them: admin permission grants are access state the
# target sets up for itself, not registry content a restore is meant to reproduce.
TABLES=(extension extension_version file_resource namespace namespace_membership personal_access_token signature_key_pair user_data extension_version_change)

# Stage next to OUT_DIR (same filesystem, so promoting a file below is a fast, atomic rename) and
# only move files into OUT_DIR once psql has committed. \copy writes its target file directly and
# independently of the transaction it runs in, so writing straight into OUT_DIR could otherwise
# leave it with some freshly-overwritten tables and some left over from a previous run if it failed
# partway through - a mixed, inconsistent directory that still passes import-db-dump.sh's own
# existence checks.
STAGE_DIR=$(mktemp -d "${OUT_DIR}.XXXXXX")
trap 'rm -rf "${STAGE_DIR}"' EXIT

(
  # cd into the staging directory and \copy to bare filenames, rather than embedding STAGE_DIR's
  # full path in the generated SQL text: that path is caller-controlled (it's derived from
  # OUT_DIR/the repo checkout path) and psql has no way to escape a literal "'" in it, so a path
  # containing one would break out of the quoted string \copy's TO clause needs it in.
  cd "${STAGE_DIR}"
  {
    echo "BEGIN;"
    # One snapshot for every table below, however long the export takes - concurrent writes on the
    # source never become visible mid-export, so e.g. extension_version and file_resource can't end
    # up looking at different points in time relative to each other.
    echo "SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY;"
    for t in "${TABLES[@]}"; do
      echo "\\copy ${t} to '${t}.csv' with (format text, delimiter ',')"
    done
    echo "COMMIT;"
    # -X: a ~/.psqlrc loads after -v ON_ERROR_STOP=1 and could override it (or AUTOCOMMIT, or
    # anything else that changes how a \copy failure is handled), which would let a broken export
    # get published as if it were complete - same reasoning as import-db-dump.sh's own target_psql.
  } | psql -X "${SOURCE_DB_URL}" -v ON_ERROR_STOP=1
)

# Publish only the files this script owns, one at a time, rather than replacing OUT_DIR wholesale -
# scrub-db-dump.sh deliberately leaves *.csv.bak originals alongside the redacted files until
# they've been reviewed, and a whole-directory swap would silently delete those (and anything else
# already there) the moment this script is re-run to refresh the dump.
#
# Not atomic as a set: an mv failing (or the script being interrupted) partway through can leave
# some tables refreshed and others on the previous export. Accepted rather than closed with a
# directory-level swap, since that trades a stale CSV of this tool's own regenerable output - fixed
# by re-running the export - for silently destroying files the export doesn't own.
for t in "${TABLES[@]}"; do
  mv -f "${STAGE_DIR}/${t}.csv" "${OUT_DIR}/${t}.csv"
done

echo "Done. Dump files in ${OUT_DIR}:"
ls -la "${OUT_DIR}"/*.csv
