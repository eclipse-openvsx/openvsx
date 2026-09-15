#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
PROJECT_ROOT=$( dirname "${SCRIPT_DIR}" )

usage() {
  local USAGE
  USAGE="
Usage: $(basename "${0}") [options]

Loads a per-table Postgres COPY-TEXT dump (see scrub-db-dump.sh) into a Postgres instance - e.g.
the one 'docker compose --profile db up postgres' starts, matching
server/src/dev/resources/application.yml's connection details, which are this script's defaults.
The target can be either completely empty or already fully migrated - see below.

Deletes all existing rows from the 8 tables the dump covers first (children before parents, so no
foreign key elsewhere in that same set gets violated), then imports every dump file and fixes up
each table's id sequence so the running app doesn't collide with the imported ids on its next
insert. The whole delete+import runs as ONE transaction: if anything fails partway through -
including a table outside these 8 that has its own foreign key into one of them, which a plain
DELETE (deliberately not CASCADE) will refuse to run past - nothing changes on the target at all.

This does NOT figure out which Flyway version the dump predates (scrub-db-dump.sh already needed
to for its own reasons - if you still have that terminal output, reuse it here with -t). It uses
whatever's given via -t, or migrates a throwaway scratch container through the full history to
detect it, purely to learn each table's dump-era column *names*.

What happens next depends on the target's own state, checked by looking for these 8 tables (not
by table count, so unrelated tables - e.g. spring_session - don't confuse it):
  - None of the 8 exist: the target is treated as a fresh database and migrated directly, via a
    real 'flyway migrate -target=<detected version>' run against it, to the dump's own Flyway
    version - not to the latest one. Afterwards its schema is an exact match for the dump, so
    there's nothing to adapt. If you want the target on the current schema afterwards (e.g. to run
    the app against it), run 'flyway migrate' (no -target) yourself once this script is done.
    This migrate also applies server/src/dev/resources/db/migration (the 'dev' Gradle source
    set's own migrations, normally only applied by running the app locally that way) alongside
    the main ones, so e.g. V1_62_1's rate-limit tier/customer seed data ends up present too, the
    same as a real local dev run would have it. The one exception is the 'super_user' /
    'super_token' seed (V1_0_1 + V1_1_1): it touches user_data/personal_access_token, so the
    DELETE+import below wipes it regardless of having run - deliberately not reseeded afterwards
    (its own migrations' hardcoded id isn't guaranteed free in real dump data, and it's not worth
    reconstructing outside of a real flyway migrate just for this).
  - All 8 already exist: the target is assumed to already be on the current schema (the app or
    'flyway migrate' got it there), same as before. The dump's old column layout is adapted to fit
    via an explicit column list per \\copy, so newer columns the target has gained since simply
    keep their column default (with known exceptions backfilled - see KNOWN_BACKFILLS below). A
    column the target has *dropped* since can't be adapted that way - there's nowhere to put its
    data - so those are detected up front and the run is refused with guidance, rather than
    failing deep inside the \\copy with a bare 'column does not exist'.
  - Some but not all 8 exist: refused - this is neither state above, and guessing which columns
    are missing on a half-migrated schema is more likely to corrupt data than help.
Either way, the target is only ever touched starting at the DELETE+import step (or, for a fresh
target, the flyway migrate right before it).

Options:
  -d <dir>       Dump directory (default: db/dump under the repo root)
  -t <version>   Skip auto-detection; the dump is from this Flyway version (e.g. 1.71)
  -H <host>      Target Postgres host (default: localhost)
  -p <port>      Target Postgres port (default: 5432)
  -U <user>      Target Postgres user (default: openvsx)
  -W <password>  Target Postgres password (default: openvsx)
  -n <database>  Target Postgres database name (default: postgres)
  -y             Skip the confirmation prompt
  -h             show this help
"
  echo "$USAGE"
  exit 1
}

DUMP_DIR="${PROJECT_ROOT}/db/dump"
FLYWAY_TARGET=""
TARGET_HOST="localhost"
TARGET_PORT="5432"
TARGET_USER="openvsx"
TARGET_PASSWORD="openvsx"
TARGET_DB="postgres"
ASSUME_YES=false

while getopts "d:t:H:p:U:W:n:yh" o; do
  case "${o}" in
    d) DUMP_DIR="${OPTARG}" ;;
    t) FLYWAY_TARGET="${OPTARG}" ;;
    H) TARGET_HOST="${OPTARG}" ;;
    p) TARGET_PORT="${OPTARG}" ;;
    U) TARGET_USER="${OPTARG}" ;;
    W) TARGET_PASSWORD="${OPTARG}" ;;
    n) TARGET_DB="${OPTARG}" ;;
    y) ASSUME_YES=true ;;
    *) usage ;;
  esac
done

for tool in docker python3; do
  command -v "${tool}" >/dev/null || { echo "This script needs '${tool}' on PATH." >&2; exit 1; }
done

MIGRATIONS_DIR="${PROJECT_ROOT}/server/src/main/resources/db/migration"
# The 'dev' Gradle source set's own migrations (server/build.gradle) - not part of the real app's
# jar, only ever applied when running the app via that source set locally. Interleaved by version
# among the main ones above (e.g. V1_0_1, V1_1_1 sort between main's V1 and V1_1). Used below only
# for a freshly-migrated target, to match what running the app locally would have applied.
DEV_MIGRATIONS_DIR="${PROJECT_ROOT}/server/src/dev/resources/db/migration"
FLYWAY_IMAGE="flyway/flyway:10"
POSTGRES_IMAGE="postgres:16.2"
RUN_ID="import-$$"
SCRATCH_CONTAINER="openvsx-${RUN_ID}"
SCRATCH_NETWORK="openvsx-${RUN_ID}-net"
COPY_FORMAT="format text, delimiter ','"

# All 8 tables scrub-db-dump.sh's dump files cover, dependency order computed dynamically below
# (do not assume it here - the target's actual constraints are authoritative).
TABLES=(extension extension_version file_resource namespace namespace_membership personal_access_token signature_key_pair user_data)

for t in "${TABLES[@]}"; do
  if [ ! -f "${DUMP_DIR}/${t}.csv" ]; then
    echo "Missing ${DUMP_DIR}/${t}.csv" >&2
    exit 1
  fi
done

WORKDIR=$(mktemp -d)
cleanup() {
  docker rm -f "${SCRATCH_CONTAINER}" >/dev/null 2>&1 || true
  docker network rm "${SCRATCH_NETWORK}" >/dev/null 2>&1 || true
  rm -rf "${WORKDIR}"
}
trap cleanup EXIT

scratch_psql() {
  docker exec -i "${SCRATCH_CONTAINER}" psql -U openvsx -d postgres -v ON_ERROR_STOP=1 "$@"
}

# --- "localhost" (the default, and what naturally refers to the caller's own machine) means
# something different from inside a container, so a throwaway container needs it rewritten to
# Docker's host.docker.internal - resolved via --add-host=host.docker.internal:host-gateway,
# which Docker Desktop supports natively and Linux's Docker Engine (20.10+) supports via that
# flag. Deliberately NOT --network host: that fails outright ("cannot share the host's network
# namespace") wherever user namespaces are enabled, which is common enough not to assume against.
# Any other host (a LAN address, a docker-compose service name on a network already joined, ...)
# is used as given - it means the same thing inside a container as outside one.
target_docker_host() {
  case "${TARGET_HOST}" in
    localhost|127.0.0.1|::1) echo "host.docker.internal" ;;
    *) echo "${TARGET_HOST}" ;;
  esac
}

# --- Run the psql client against the target, either the host's own psql or, if that isn't
# installed, a throwaway containerized one (see target_docker_host above for how it reaches the
# target).
# -X because the first branch runs the caller's own psql: a ~/.psqlrc that turns on \timing, or
# echoes anything at all, would be read back as schema metadata by the queries below.
target_psql() {
  if command -v psql >/dev/null 2>&1; then
    PGPASSWORD="${TARGET_PASSWORD}" psql -X -h "${TARGET_HOST}" -p "${TARGET_PORT}" -U "${TARGET_USER}" -d "${TARGET_DB}" -v ON_ERROR_STOP=1 "$@"
  else
    # -f references a file under WORKDIR; -c/\copy reference dump files under DUMP_DIR - both
    # need to be visible inside the container at the same absolute path the SQL text uses.
    docker run --rm -i --add-host=host.docker.internal:host-gateway \
      -v "${DUMP_DIR}:${DUMP_DIR}:ro" -v "${WORKDIR}:${WORKDIR}" \
      -e PGPASSWORD="${TARGET_PASSWORD}" "${POSTGRES_IMAGE}" \
      psql -X -h "$(target_docker_host)" -p "${TARGET_PORT}" -U "${TARGET_USER}" -d "${TARGET_DB}" -v ON_ERROR_STOP=1 "$@"
  fi
}

# --- Migrate the TARGET itself (not the scratch container) directly to a given Flyway version,
# for the "target is empty" case below. Includes the dev migrations (see DEV_MIGRATIONS_DIR above)
# alongside the main ones, if that directory exists, so a fresh target ends up the same way
# running the app locally would have left it - including e.g. V1_62_1's rate-limit tier/customer
# seed data, which isn't part of any of these 8 dump tables so survives the import step untouched.
# The one exception is V1_0_1/V1_1_1's 'super_user' seed, which DOES touch two of these 8 tables
# (user_data, personal_access_token) and so gets wiped by the DELETE+import below regardless of
# having been applied here - deliberately left that way, not reseeded afterwards.
target_flyway_migrate() {
  local target="$1"
  local mounts=(-v "${MIGRATIONS_DIR}:/flyway/sql/main:ro")
  local locations="filesystem:/flyway/sql/main"
  if [ -d "${DEV_MIGRATIONS_DIR}" ]; then
    mounts+=(-v "${DEV_MIGRATIONS_DIR}:/flyway/sql/dev:ro")
    locations="${locations},filesystem:/flyway/sql/dev"
  fi
  docker run --rm --add-host=host.docker.internal:host-gateway "${mounts[@]}" "${FLYWAY_IMAGE}" \
    -url="jdbc:postgresql://$(target_docker_host):${TARGET_PORT}/${TARGET_DB}" \
    -user="${TARGET_USER}" -password="${TARGET_PASSWORD}" -locations="${locations}" \
    -connectRetries=10 ${target:+-target="${target}"} migrate >/dev/null
}

echo "Checking the target is reachable (${TARGET_HOST}:${TARGET_PORT}/${TARGET_DB})..."
target_psql -c "select 1;" >/dev/null

# --- Learn the dump's column names per table, without ever touching the target for this part.
echo "Starting a scratch Postgres to read the dump's schema..."
docker network create "${SCRATCH_NETWORK}" >/dev/null
docker run -d --name "${SCRATCH_CONTAINER}" --network "${SCRATCH_NETWORK}" \
  -e POSTGRES_USER=openvsx -e POSTGRES_PASSWORD=openvsx "${POSTGRES_IMAGE}" >/dev/null
until docker exec "${SCRATCH_CONTAINER}" pg_isready -U openvsx >/dev/null 2>&1; do sleep 1; done

scratch_migrate() {
  local target="$1"
  docker run --rm --network "${SCRATCH_NETWORK}" -v "${MIGRATIONS_DIR}:/flyway/sql" "${FLYWAY_IMAGE}" \
    -url="jdbc:postgresql://${SCRATCH_CONTAINER}:5432/postgres" -user=openvsx -password=openvsx \
    -connectRetries=10 ${target:+-target="${target}"} migrate >/dev/null
}

scratch_reset() {
  docker exec "${SCRATCH_CONTAINER}" psql -U openvsx -d postgres -c \
    "DROP SCHEMA public CASCADE; CREATE SCHEMA public;" >/dev/null
}

table_column_count() {
  scratch_psql -t -c "select count(*) from information_schema.columns where table_schema='public' and table_name='${1}';" | tr -d ' '
}

table_column_list() {
  scratch_psql -t -c "select string_agg(column_name, ',' order by ordinal_position) from information_schema.columns where table_schema='public' and table_name='${1}';" | tr -d ' '
}

if [ -z "${FLYWAY_TARGET}" ]; then
  echo "No -t given, auto-detecting the Flyway version this dump matches..."

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

  declare -A actual_cols
  for t in "${TABLES[@]}"; do
    actual_cols["${t}"]=$(detect_actual_cols "${t}")
  done
  echo "  dump column counts: $(for t in "${TABLES[@]}"; do printf '%s=%s ' "${t}" "${actual_cols[${t}]}"; done)"

  mapfile -t VERSIONS < <(
    find "${MIGRATIONS_DIR}" -maxdepth 1 -name 'V*__*' -printf '%f\n' \
      | sed -E 's/^V([0-9_]+)__.*/\1/; s/_/./' \
      | sort -t. -k1,1n -k2,2n
  )

  scratch_reset
  for v in "${VERSIONS[@]}"; do
    scratch_migrate "${v}"
    match=true
    for t in "${TABLES[@]}"; do
      if [ "$(table_column_count "${t}")" != "${actual_cols[${t}]}" ]; then
        match=false
        break
      fi
    done
    if [ "${match}" = true ]; then
      FLYWAY_TARGET="${v}"
      break
    fi
  done

  if [ -z "${FLYWAY_TARGET}" ]; then
    echo "Could not find a single Flyway version matching all 8 tables' column counts." >&2
    echo "Pass -t to pin a version yourself." >&2
    exit 1
  fi
  echo "  detected Flyway target: ${FLYWAY_TARGET}"
else
  scratch_reset
  scratch_migrate "${FLYWAY_TARGET}"
fi

declare -A COLUMN_LISTS
for t in "${TABLES[@]}"; do
  COLUMN_LISTS["${t}"]=$(table_column_list "${t}")
  echo "  ${t}: (${COLUMN_LISTS[${t}]})"
done

# --- Decide whether the target is a fresh database (none of the 8 tables exist yet) or one
# that's presumably already fully migrated (all 8 exist). Checked by table presence, not a bare
# table count, so other tables the target happens to have (spring_session, flyway_schema_history,
# ...) don't affect the decision either way.
echo "Checking whether the target already has these 8 tables..."
present=0
declare -a MISSING_TABLES=()
for t in "${TABLES[@]}"; do
  exists=$(target_psql -t -c "select (to_regclass('public.${t}') is not null);" | tr -d ' ')
  if [ "${exists}" = "t" ]; then
    present=$((present + 1))
  else
    MISSING_TABLES+=("${t}")
  fi
done

if [ "${present}" -eq 0 ]; then
  echo "  none present - treating the target as a fresh database."
  echo "Migrating the target directly to Flyway version ${FLYWAY_TARGET} (the dump's own schema -"
  echo "not necessarily the latest one; run 'flyway migrate' yourself afterwards for that)..."
  target_flyway_migrate "${FLYWAY_TARGET}"
elif [ "${#MISSING_TABLES[@]}" -eq 0 ]; then
  echo "  all present - assuming the target is already on the current schema."
else
  echo "" >&2
  echo "The target has some but not all of these 8 tables - missing: ${MISSING_TABLES[*]}." >&2
  echo "That's neither a fresh database nor a fully migrated one, so this script won't guess." >&2
  echo "Point it at an empty database, or one already fully migrated via 'flyway migrate'." >&2
  exit 1
fi

# --- A column the dump has that the target no longer does (dropped by a migration between the
# dump's era and the target's) has nowhere for its data to go, and naming it in the \copy column
# list would fail with a bare 'column does not exist' well into the run. Caught here instead,
# before anything on the target is touched, with a pointer at the import-into-empty-DB route
# that lets the real migrations decide what becomes of those columns.
#
# sed rather than grep to strip the blank line psql's tuples-only output leaves behind: grep exits 1
# when it matches nothing, which under `pipefail` is indistinguishable from psql itself having
# failed, and the `|| true` needed to tolerate the former swallowed the latter too - leaving the
# check silently concluding that nothing was dropped. sed exits 0 either way, so a psql that cannot
# reach the target aborts the script here instead. Same in the gap query below.
echo "Checking that every column in the dump still exists on the target..."
declare -a DROPPED_COLUMNS=()
for t in "${TABLES[@]}"; do
  IFS=',' read -r -a dump_cols <<< "${COLUMN_LISTS[${t}]}"
  gone_cols=$(target_psql -t -c "
    select c from unnest(array[$(printf "'%s'," "${dump_cols[@]}" | sed 's/,$//')]) as c
    where c not in (
      select column_name from information_schema.columns
      where table_schema='public' and table_name='${t}'
    );
  " | tr -d ' ' | sed '/^$/d')
  while IFS= read -r col; do
    [ -z "${col}" ] && continue
    DROPPED_COLUMNS+=("${t}.${col}")
  done <<< "${gone_cols}"
done

if [ "${#DROPPED_COLUMNS[@]}" -gt 0 ]; then
  echo "" >&2
  echo "These columns are in the dump but no longer on the target - its schema is newer than the" >&2
  echo "dump's and has dropped them since (user_data.eclipse_data went in V1_44, for instance," >&2
  echo "extension.latest_id in V1_22, file_resource.content in V1_65):" >&2
  printf '  %s\n' "${DROPPED_COLUMNS[@]}" >&2
  echo "" >&2
  echo "There's nowhere to import their data, and silently dropping it isn't this script's call." >&2
  echo "Point it at an empty database instead: that gets migrated to the dump's own version" >&2
  echo "(${FLYWAY_TARGET}) and imports cleanly, and a plain 'flyway migrate' afterwards moves it" >&2
  echo "forward with the real migrations deciding what happens to these columns. Nothing has been" >&2
  echo "touched on the target." >&2
  exit 1
fi

# --- A column the target has gained since the dump's era is left out of the \copy column list, and
# the imported rows take whatever the target's schema gives them - its default, or NULL. Two kinds of
# column need more than that, and both are handled by a backfill registered here.
#
# A column that is NOT NULL with no default can't be left out at all: Postgres would reject every
# imported row. Those are dropped to nullable, imported, backfilled with the same UPDATE the
# introducing migration used, and set back to NOT NULL - all inside the one transaction. A gap of
# this kind with no entry here fails the preflight check below instead of guessing.
#
# A column whose default does not reproduce what the introducing migration computed needs a backfill
# too, even though the import would succeed without one. personal_access_token.type is the case in
# point: V1_74 defaults it to 'LLT' so hand-written INSERTs keep working, but V1_72 derived 'OTT' for
# one-time tokens from their description, and importing those as 'LLT' would quietly turn a
# used-once credential into a permanent one. So the gap set below is every target column the dump
# lacks, and only the un-importable ones are *required* to have an entry.
#
# Add an entry (keyed "table.column") for anything new, matching whatever UPDATE the migration that
# introduced the column used for pre-existing rows.
declare -A KNOWN_BACKFILLS
KNOWN_BACKFILLS["personal_access_token.version"]="UPDATE personal_access_token SET version = 0;"
KNOWN_BACKFILLS["personal_access_token.type"]="
  UPDATE personal_access_token SET type = 'OTT' WHERE description = 'One time use publish token';
  UPDATE personal_access_token SET type = 'LLT' WHERE type IS NULL;
"

echo "Checking for target columns the dump doesn't have..."
declare -A BACKFILL_COLUMNS   # "table.column" -> 1, for every gap that has a backfill to run
declare -A REQUIRED_GAPS      # "table.column" -> 1, the subset that can't be imported without one
declare -a UNKNOWN_GAPS=()
for t in "${TABLES[@]}"; do
  IFS=',' read -r -a dump_cols <<< "${COLUMN_LISTS[${t}]}"
  gap_cols=$(target_psql -t -A -F',' -c "
    select column_name, (is_nullable = 'NO' and column_default is null)
    from information_schema.columns
    where table_schema='public' and table_name='${t}'
    and column_name not in ($(printf "'%s'," "${dump_cols[@]}" | sed 's/,$//'));
  " | sed '/^$/d')
  while IFS=',' read -r col required; do
    [ -z "${col}" ] && continue
    key="${t}.${col}"
    [ "${required}" = "t" ] && REQUIRED_GAPS["${key}"]=1
    if [ -n "${KNOWN_BACKFILLS[${key}]:-}" ]; then
      BACKFILL_COLUMNS["${key}"]=1
      if [ "${required}" = "t" ]; then
        echo "  ${key}: no value in the dump, not nullable, no default - will backfill (known)"
      else
        echo "  ${key}: no value in the dump - will backfill (known) instead of taking the default"
      fi
    elif [ "${required}" = "t" ]; then
      UNKNOWN_GAPS+=("${key}")
    fi
  done <<< "${gap_cols}"
done

if [ "${#UNKNOWN_GAPS[@]}" -gt 0 ]; then
  echo "" >&2
  echo "These target columns are NOT NULL with no default, have no value in the dump, and have" >&2
  echo "no known backfill in this script's KNOWN_BACKFILLS table:" >&2
  printf '  %s\n' "${UNKNOWN_GAPS[@]}" >&2
  echo "" >&2
  echo "Add an entry for each, matching whatever UPDATE the migration that introduced the column" >&2
  echo "used to backfill pre-existing rows (check server/src/main/resources/db/migration). Nothing" >&2
  echo "has been touched on the target." >&2
  exit 1
fi

# --- Compute a safe delete order from the TARGET's actual foreign keys among these 8 tables -
# not assumed, and not necessarily the dump-era graph, since the target may be on a newer schema
# than the dump (the "already fully migrated" case above; a freshly-migrated target's graph is the
# dump-era one exactly, which this reads just as well). A table can be deleted once nothing else
# still-undeleted references it;
# any foreign key from OUTSIDE this set of 8 into one of them is deliberately left for Postgres
# to enforce (see the module docstring above on why this isn't CASCADE).
echo "Computing a safe delete order from the target's current foreign keys..."
edges=$(target_psql -t -A -F',' -c "
  select tc.table_name, ccu.table_name
  from information_schema.table_constraints tc
  join information_schema.key_column_usage kcu on tc.constraint_name = kcu.constraint_name
  join information_schema.constraint_column_usage ccu on tc.constraint_name = ccu.constraint_name
  where tc.constraint_type = 'FOREIGN KEY'
  and tc.table_name in ($(printf "'%s'," "${TABLES[@]}" | sed 's/,$//'))
  and ccu.table_name in ($(printf "'%s'," "${TABLES[@]}" | sed 's/,$//'))
  and tc.table_name != ccu.table_name;
")

DELETE_ORDER=$(python3 - "${TABLES[*]}" <<PYEOF
import sys
tables = set(sys.argv[1].split())
edges = []
for line in """${edges}""".strip().splitlines():
    line = line.strip()
    if not line:
        continue
    a, b = line.split(',')
    edges.append((a.strip(), b.strip()))

remaining = set(tables)
order = []
while remaining:
    referenced = {b for (a, b) in edges if a in remaining and b in remaining}
    deletable = remaining - referenced
    if not deletable:
        print(f"CYCLE among {remaining}", file=sys.stderr)
        sys.exit(1)
    for t in sorted(deletable):
        order.append(t)
        remaining.discard(t)
print(' '.join(order))
PYEOF
)
IFS=' ' read -r -a DELETE_ORDER_ARR <<< "${DELETE_ORDER}"
# Imports must go the other way - parents before the children that reference them, e.g.
# extension_version can't be inserted before personal_access_token exists for it to reference.
IMPORT_ORDER_ARR=()
for ((i = ${#DELETE_ORDER_ARR[@]} - 1; i >= 0; i--)); do
  IMPORT_ORDER_ARR+=("${DELETE_ORDER_ARR[i]}")
done
echo "  delete order: ${DELETE_ORDER_ARR[*]}"
echo "  import order: ${IMPORT_ORDER_ARR[*]}"

echo ""
echo "About to DELETE ALL ROWS from these tables in ${TARGET_USER}@${TARGET_HOST}:${TARGET_PORT}/${TARGET_DB}:"
printf '  %s\n' "${TABLES[@]}"
echo "and reload them from ${DUMP_DIR}."
if [ "${ASSUME_YES}" = false ]; then
  read -r -p "Continue? [y/N] " reply
  case "${reply}" in
    [yY]|[yY][eE][sS]) ;;
    *) echo "Aborted, nothing changed."; exit 1 ;;
  esac
fi

SQL_FILE="${WORKDIR}/reload.sql"
{
  echo "BEGIN;"
  for t in "${DELETE_ORDER_ARR[@]}"; do
    [ -n "${t}" ] && echo "DELETE FROM ${t};"
  done
  for t in "${IMPORT_ORDER_ARR[@]}"; do
    # Only the columns the dump can't supply at all lose their NOT NULL for the duration of the
    # import; a column that has a default imports fine as it is and just gets its backfill after.
    required_cols_for_table=()
    for key in "${!REQUIRED_GAPS[@]}"; do
      [ "${key%%.*}" = "${t}" ] && required_cols_for_table+=("${key#*.}")
    done
    backfill_cols_for_table=()
    for key in "${!BACKFILL_COLUMNS[@]}"; do
      [ "${key%%.*}" = "${t}" ] && backfill_cols_for_table+=("${key#*.}")
    done
    for col in "${required_cols_for_table[@]}"; do
      echo "ALTER TABLE ${t} ALTER COLUMN ${col} DROP NOT NULL;"
    done
    echo "\\copy ${t} (${COLUMN_LISTS[${t}]}) from '${DUMP_DIR}/${t}.csv' with (${COPY_FORMAT})"
    for col in "${backfill_cols_for_table[@]}"; do
      echo "${KNOWN_BACKFILLS[${t}.${col}]}"
    done
    for col in "${required_cols_for_table[@]}"; do
      echo "ALTER TABLE ${t} ALTER COLUMN ${col} SET NOT NULL;"
    done
  done
  for t in "${TABLES[@]}"; do
    # Not every table's id is sequence-backed (or has an "id" column at all, though all 8 here
    # do) - pg_get_serial_sequence returns NULL rather than erroring for those, so the setval
    # call is skipped for them via the WHERE-less guard below.
    cat <<SQL
DO \$\$
DECLARE
  seq_name text := pg_get_serial_sequence('${t}', 'id');
  max_id bigint;
BEGIN
  IF seq_name IS NOT NULL THEN
    SELECT COALESCE(MAX(id), 0) INTO max_id FROM ${t};
    PERFORM setval(seq_name, GREATEST(max_id, 1));
  END IF;
END \$\$;
SQL
  done
  echo "COMMIT;"
} > "${SQL_FILE}"

echo "Reloading (single transaction - any failure rolls everything back)..."
target_psql -f "${SQL_FILE}"

echo ""
echo "Done. Row counts now on the target:"
target_psql -t -c "$(for t in "${TABLES[@]}"; do echo "select '${t}: ' || count(*) from ${t};"; done)"
