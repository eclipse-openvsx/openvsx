#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $(basename "$0") <source-connection-string> [output-dir]" >&2
  echo "  e.g.: $(basename "$0") 'postgresql://user@prod-host:5432/openvsx' db/dump" >&2
  exit 1
}

[ $# -ge 1 ] || usage
SOURCE_DB_URL="$1"
OUT_DIR="${2:-db/dump}"
mkdir -p "${OUT_DIR}"
OUT_DIR="$(cd "${OUT_DIR}" && pwd)"

# The same 9 tables, and the same per-table "format text, delimiter ','" layout, import-db-dump.sh
# reloads from db/dump - see that script for how these get loaded back (and scrub-db-dump.sh for
# how the 3 sensitive ones among them get redacted first).
TABLES=(extension extension_version file_resource namespace namespace_membership personal_access_token signature_key_pair user_data extension_version_change)

{
  echo "BEGIN;"
  # One snapshot for every table below, however long the export takes - concurrent writes on the
  # source never become visible mid-export, so e.g. extension_version and file_resource can't end
  # up looking at different points in time relative to each other.
  echo "SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY;"
  for t in "${TABLES[@]}"; do
    echo "\\copy ${t} to '${OUT_DIR}/${t}.csv' with (format text, delimiter ',')"
  done
  echo "COMMIT;"
} | psql "${SOURCE_DB_URL}" -v ON_ERROR_STOP=1

echo "Done. Dump files in ${OUT_DIR}:"
ls -la "${OUT_DIR}"/*.csv
