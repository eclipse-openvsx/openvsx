#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
PROJECT_ROOT=$( dirname "${SCRIPT_DIR}" )

usage() {
  local USAGE
  USAGE="
Usage: $(basename "${0}") [options]

Redacts sensitive columns from a per-table Postgres COPY-TEXT dump (as produced by
'\\copy <table> to ... with (format text, delimiter '\',\'')') before it's shared or used for
local dev/testing. Only touches personal_access_token.csv, signature_key_pair.csv and
user_data.csv - the other dump files have no columns in the redaction list below and are left
untouched.

What gets redacted (see the REDACTION step further down to change this):
  personal_access_token.value   -> deterministic fake hash, unique per row
  signature_key_pair.private_key -> 32 zero bytes (this column is NOT NULL)
  user_data.email                -> synthetic, unique per row
  user_data.full_name            -> synthetic, unique per row
  user_data.avatar_url           -> cleared
  user_data.auth_id              -> synthetic, unique per row (only where already non-null)
  user_data.eclipse_token        -> cleared (has held raw OAuth access/refresh token JSON)
  user_data.eclipse_person_id    -> cleared
  user_data.login_name is left untouched - it's the public registry username already.

How it works: this dump's exact column layout depends on which Flyway migration was HEAD when
it was taken (columns get added/dropped over time, and Postgres never reuses a dropped column's
position). The script spins up a scratch Postgres container and walks forward through every
migration from server/src/main/resources/db/migration one version at a time, checking the three
tables' real column counts after each one, stopping at the first version whose counts match the
dump files - no guessing, and no assumption that a future dump is from the same schema version
this was built against. That walk is O(n) migrations total either way (each step only applies
whatever's newly pending), just possibly with extra column-count checks along the way. Pass -t to
skip detection and pin a version directly (faster, and necessary if detection is ambiguous - e.g.
two versions in a row happen to match on count alone).

The scratch database only ever holds these three tables' data (plus whatever they cascade to via
foreign keys) and is destroyed when the script exits, successfully or not.

Options:
  -d <dir>       Dump directory (default: db/dump under the repo root)
  -t <version>   Skip auto-detection; migrate to this Flyway version directly (e.g. 1.71)
  -f             Overwrite existing *.bak backups from a previous run instead of refusing to run
  -h             show this help

If a file fails to import (Postgres reports a COPY parse error), the script stops and prints that
error verbatim - it names the exact offending line and content. This is NOT auto-repaired: past
corruption in this dump has been genuine data bugs (a missing closing quote, a stray unescaped
quote) with no reliable general fix, and guessing wrong on a redaction pass is worse than
stopping. Open the reported line, fix it by hand, and re-run.
"
  echo "$USAGE"
  exit 1
}

DUMP_DIR="${PROJECT_ROOT}/db/dump"
FLYWAY_TARGET=""
FORCE=false

while getopts "d:t:fh" o; do
  case "${o}" in
    d) DUMP_DIR="${OPTARG}" ;;
    t) FLYWAY_TARGET="${OPTARG}" ;;
    f) FORCE=true ;;
    *) usage ;;
  esac
done

for tool in docker python3; do
  command -v "${tool}" >/dev/null || { echo "This script needs '${tool}' on PATH." >&2; exit 1; }
done

MIGRATIONS_DIR="${PROJECT_ROOT}/server/src/main/resources/db/migration"
FLYWAY_IMAGE="flyway/flyway:10"
POSTGRES_IMAGE="postgres:16.2"
RUN_ID="scrub-$$"
CONTAINER_NAME="openvsx-${RUN_ID}"
NETWORK_NAME="openvsx-${RUN_ID}-net"
COPY_FORMAT="format text, delimiter ','"

# Tables touched by this script, in dependency order for import (user_data must be present
# before personal_access_token, which references it by foreign key).
TABLES=(user_data signature_key_pair personal_access_token)

for f in "${TABLES[@]}"; do
  if [ ! -f "${DUMP_DIR}/${f}.csv" ]; then
    echo "Missing ${DUMP_DIR}/${f}.csv" >&2
    exit 1
  fi
  if [ "$FORCE" = false ] && [ -f "${DUMP_DIR}/${f}.csv.bak" ]; then
    echo "${DUMP_DIR}/${f}.csv.bak already exists from a previous run - pass -f to overwrite it, or remove/move it first." >&2
    exit 1
  fi
done

WORKDIR=$(mktemp -d)
cleanup() {
  docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
  docker network rm "${NETWORK_NAME}" >/dev/null 2>&1 || true
  rm -rf "${WORKDIR}"
}
trap cleanup EXIT

psql_exec() {
  docker exec -i "${CONTAINER_NAME}" psql -U openvsx -d postgres -v ON_ERROR_STOP=1 "$@"
}

echo "Starting scratch Postgres..."
docker network create "${NETWORK_NAME}" >/dev/null
docker run -d --name "${CONTAINER_NAME}" --network "${NETWORK_NAME}" \
  -e POSTGRES_USER=openvsx -e POSTGRES_PASSWORD=openvsx "${POSTGRES_IMAGE}" >/dev/null
until docker exec "${CONTAINER_NAME}" pg_isready -U openvsx >/dev/null 2>&1; do sleep 1; done

flyway_migrate() {
  local target="$1"
  docker run --rm --network "${NETWORK_NAME}" -v "${MIGRATIONS_DIR}:/flyway/sql" "${FLYWAY_IMAGE}" \
    -url="jdbc:postgresql://${CONTAINER_NAME}:5432/postgres" -user=openvsx -password=openvsx \
    -connectRetries=10 ${target:+-target="${target}"} migrate >/dev/null
}

reset_schema() {
  docker exec "${CONTAINER_NAME}" psql -U openvsx -d postgres -c \
    "DROP SCHEMA public CASCADE; CREATE SCHEMA public;" >/dev/null
}

table_column_count() {
  psql_exec -t -c "select count(*) from information_schema.columns where table_schema='public' and table_name='${1}';" | tr -d ' '
}

if [ -z "${FLYWAY_TARGET}" ]; then
  echo "No -t given, auto-detecting the Flyway version this dump matches..."

  # Rough column count per dump file: the mode across many rows, tolerating a handful of
  # malformed outliers (like the corrupted rows this dump has had before) without needing to
  # correctly parse them - this is only used to pick a migration target, not for the redaction
  # itself, which always goes through Postgres's own COPY parser.
  detect_actual_cols() {
    python3 - "${DUMP_DIR}/${1}.csv" <<'PY'
import csv, sys, collections
counts = collections.Counter()
with open(sys.argv[1], newline='', encoding='utf-8', errors='replace') as f:
    # Postgres TEXT format has no quoting at all (unlike CSV) - only backslash-escaping of the
    # delimiter, backslash itself, and embedded newlines/carriage returns. QUOTE_NONE keeps a
    # literal '"' in a field (e.g. inside README/HTML content) from being misread as a quote.
    reader = csv.reader(f, delimiter=',', escapechar='\\', quoting=csv.QUOTE_NONE)
    for i, row in enumerate(reader):
        if i >= 2000:
            break
        counts[len(row)] += 1
print(counts.most_common(1)[0][0])
PY
  }

  actual_pat=$(detect_actual_cols personal_access_token)
  actual_skp=$(detect_actual_cols signature_key_pair)
  actual_ud=$(detect_actual_cols user_data)
  echo "  dump column counts: personal_access_token=${actual_pat} signature_key_pair=${actual_skp} user_data=${actual_ud}"

  # Every migration version, oldest first, exactly as Flyway orders them (matches the V<n>__ /
  # V<n>_<m>__ filenames under db/migration). Walked forward one -target=X call at a time -
  # each call only applies whatever is newly pending, so this is one full migration replay
  # overall, not O(n^2) work, and every state actually comes from a real `flyway migrate`
  # rather than from custom bookkeeping.
  mapfile -t VERSIONS < <(
    find "${MIGRATIONS_DIR}" -maxdepth 1 -name 'V*__*' -printf '%f\n' \
      | sed -E 's/^V([0-9_]+)__.*/\1/; s/_/./' \
      | sort -t. -k1,1n -k2,2n
  )

  reset_schema
  for v in "${VERSIONS[@]}"; do
    flyway_migrate "${v}"
    pat=$(table_column_count personal_access_token)
    skp=$(table_column_count signature_key_pair)
    ud=$(table_column_count user_data)
    if [ "${pat}" = "${actual_pat}" ] && [ "${skp}" = "${actual_skp}" ] && [ "${ud}" = "${actual_ud}" ]; then
      FLYWAY_TARGET="${v}"
      break
    fi
  done

  if [ -z "${FLYWAY_TARGET}" ]; then
    echo "Could not find a single Flyway version matching all three tables' column counts." >&2
    echo "This can happen if the dump predates the oldest migration, or if the three files come" >&2
    echo "from different points in time. Pass -t to pin a version yourself." >&2
    exit 1
  fi
  echo "  detected Flyway target: ${FLYWAY_TARGET}"
  reset_schema
fi

echo "Migrating scratch schema to Flyway version ${FLYWAY_TARGET}..."
reset_schema
flyway_migrate "${FLYWAY_TARGET}"

echo "Importing dump files..."
for t in "${TABLES[@]}"; do
  docker cp "${DUMP_DIR}/${t}.csv" "${CONTAINER_NAME}:/tmp/${t}.csv"
  set +e
  import_output=$(psql_exec -c "\\copy ${t} from '/tmp/${t}.csv' with (${COPY_FORMAT})" 2>&1)
  import_status=$?
  set -e
  if [ ${import_status} -ne 0 ]; then
    echo "" >&2
    echo "Import of ${t}.csv failed:" >&2
    echo "${import_output}" >&2
    echo "" >&2
    echo "Fix the offending line in ${DUMP_DIR}/${t}.csv by hand and re-run. Nothing has been" >&2
    echo "modified in ${DUMP_DIR} yet." >&2
    exit 1
  fi
  echo "  ${t}: ${import_output}"
done

echo "Redacting..."
psql_exec <<'SQL'
UPDATE personal_access_token
SET value = encode(sha256(('redacted-pat-' || id)::bytea), 'hex');

UPDATE signature_key_pair
SET private_key = decode(repeat('00', 32), 'hex');

UPDATE user_data
SET email = 'user' || id || '@example.invalid',
    full_name = 'Redacted User ' || id,
    avatar_url = NULL,
    eclipse_token = NULL,
    eclipse_person_id = NULL;

-- auth_id is the provider-side account identifier (GitHub's numeric user id, say). It's
-- nullable and only set for rows that actually went through an OAuth login, so this keeps the
-- column's null/non-null shape intact rather than filling every row - the dump then still
-- exercises both cases. There's no unique constraint on it (unique_user_data covers
-- (provider, login_name)), so a per-row id suffix is enough to keep it distinct.
UPDATE user_data
SET auth_id = 'redacted-' || id
WHERE auth_id IS NOT NULL;
SQL

echo "Exporting redacted tables..."
for t in "${TABLES[@]}"; do
  psql_exec -c "\\copy ${t} to '/tmp/${t}.redacted.csv' with (${COPY_FORMAT})" >/dev/null
  docker cp "${CONTAINER_NAME}:/tmp/${t}.redacted.csv" "${WORKDIR}/${t}.redacted.csv"
done

echo "Verifying the redacted export round-trips cleanly..."
reset_schema
flyway_migrate "${FLYWAY_TARGET}"
for t in "${TABLES[@]}"; do
  docker cp "${WORKDIR}/${t}.redacted.csv" "${CONTAINER_NAME}:/tmp/${t}.redacted.csv"
  round_trip=$(psql_exec -c "\\copy ${t} from '/tmp/${t}.redacted.csv' with (${COPY_FORMAT})" 2>&1)
  echo "  ${t}: ${round_trip}"
  if ! echo "${round_trip}" | grep -q "^COPY "; then
    echo "Round-trip verification failed for ${t} - refusing to touch ${DUMP_DIR}. See output above." >&2
    exit 1
  fi
done

echo "Replacing dump files (originals backed up as *.csv.bak)..."
for t in "${TABLES[@]}"; do
  cp "${DUMP_DIR}/${t}.csv" "${DUMP_DIR}/${t}.csv.bak"
  cp "${WORKDIR}/${t}.redacted.csv" "${DUMP_DIR}/${t}.csv"
done

echo "Done. Redacted: ${TABLES[*]}"
echo "Originals kept as *.csv.bak - delete them once you've confirmed the result looks right."
