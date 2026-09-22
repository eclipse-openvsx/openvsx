#!/usr/bin/env bash

# Generates access-log files the same way generate-download-logs.sh does, and, optionally, feeds
# them straight to the admin backfill endpoint (POST /admin/api/analytics/downloads/backfill)
# instead of uploading them to the Silo bucket the AWS source reads.
#
# Delegates all log generation to generate-download-logs.sh, so the two scripts cannot drift apart
# on log format, filename sourcing or randomization - this one only adds the backfill call.
set -euo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

FORMAT=cloudfront
COUNT=200
DAYS=7
# Backfill resolves filenames against the running server's OWN active storage type, unlike the AWS
# source (always 'aws'). A bare dev server without the Silo/AWS profile stores locally.
STORAGE_TYPE=local
OUT=""
BACKFILL=0
URL="http://localhost:8080"
# The dev super user's seeded token (src/dev/resources/db/migration/V1_0_1__Super_user.sql).
TOKEN=super_token
FILE_DATE=""

usage() {
    cat <<'USAGE'
Usage: generate-download-logs-backfill.sh [options]

  --format cloudfront|fastly   log format to emit (default: cloudfront)
  --count N                    number of download lines (default: 200)
  --days N                     spread timestamps over the last N days (default: 7)
  --storage-type TYPE          file_resource.storage_type to draw filenames from (default: local,
                               the dev server's own storage backend unless Silo/AWS is configured)
  --out FILE                   write the log here instead of a temp file
  --backfill                   POST the log to the admin backfill endpoint
  --url URL                    registry base URL (default: http://localhost:8080)
  --token TOKEN                admin access token (default: super_token, the dev super user's)
  --file-date DATE             fileDate query param (yyyy-mm-dd), for lines without their own
                               timestamp
  -h, --help                   this text

Examples:
  # see what would be generated
  ./generate-download-logs-backfill.sh --count 20 --out /dev/stdout

  # 500 downloads over 14 days, backfilled into analytics directly, no S3/Silo involved
  ./generate-download-logs-backfill.sh --count 500 --days 14 --backfill

Requires ovsx.analytics.enabled=true and 'docker compose --profile analytics up' on the server
under test - the backfill endpoint is not mapped otherwise, and the call below fails with 404.
USAGE
}

while [ $# -gt 0 ]; do
    case "$1" in
        --format) FORMAT="$2"; shift 2 ;;
        --count) COUNT="$2"; shift 2 ;;
        --days) DAYS="$2"; shift 2 ;;
        --storage-type) STORAGE_TYPE="$2"; shift 2 ;;
        --out) OUT="$2"; shift 2 ;;
        --backfill) BACKFILL=1; shift ;;
        --url) URL="$2"; shift 2 ;;
        --token) TOKEN="$2"; shift 2 ;;
        --file-date) FILE_DATE="$2"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "unknown option: $1" >&2; usage >&2; exit 2 ;;
    esac
done

LOG_FILE="${OUT:-$(mktemp -t ovsx-downloads-XXXXXX.log)}"

"${SCRIPT_DIR}/generate-download-logs.sh" \
    --format "$FORMAT" --count "$COUNT" --days "$DAYS" --storage-type "$STORAGE_TYPE" --out "$LOG_FILE"

if [ "$BACKFILL" -eq 0 ]; then
    exit 0
fi

QUERY="token=${TOKEN}&format=${FORMAT}"
if [ -n "$FILE_DATE" ]; then
    QUERY="${QUERY}&fileDate=${FILE_DATE}"
fi

echo "backfilling ${COUNT} ${FORMAT} download line(s) from ${LOG_FILE}..." >&2
# the parser auto-detects gzip from content, so the plain file the generator wrote needs no
# compression step here
RESPONSE=$(
    curl -sS -X POST \
        -H 'Content-Type: application/octet-stream' \
        --data-binary @"$LOG_FILE" \
        -w $'\n%{http_code}' \
        "${URL}/admin/api/analytics/downloads/backfill?${QUERY}"
)
HTTP_CODE="${RESPONSE##*$'\n'}"
BODY="${RESPONSE%$'\n'*}"

echo "$BODY"
if [ "$HTTP_CODE" != 200 ]; then
    echo "backfill request failed with HTTP ${HTTP_CODE}" >&2
    echo "if this is a 404, enable analytics first: ovsx.analytics.enabled=true and" \
        "'docker compose --profile analytics up'" >&2
    exit 1
fi
echo "backfilled into download analytics" >&2
