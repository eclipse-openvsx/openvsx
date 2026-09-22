#!/usr/bin/env bash

# Generates the same synthetic access log as generate-download-logs.sh, but ingests it through the
# admin backfill endpoint (POST /admin/api/analytics/downloads/backfill) instead of uploading it to
# the bucket the scheduled AWS source polls. Useful to exercise the backfill API itself, or to seed
# analytics without docker compose's silo bucket or waiting on the cron schedule.
#
# Unlike --upload, this needs ovsx.analytics.enabled=true (the backfill endpoint is only mapped
# when it is) - see doc/development.md.
set -euo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
GENERATE="${SCRIPT_DIR}/generate-download-logs.sh"

FORMAT=cloudfront
COUNT=200
DAYS=7
STORAGE_TYPE=aws
REGISTRY_URL="http://localhost:8080"
TOKEN=super_token
FILE_DATE=""

usage() {
    cat <<'USAGE'
Usage: backfill-download-logs.sh [options]

  --format cloudfront|fastly  log format to emit (default: cloudfront)
  --count N                   number of download lines (default: 200)
  --days N                    spread timestamps over the last N days (default: 7)
  --storage-type TYPE         file_resource.storage_type to draw filenames from (default: aws)
  --registry-url URL          registry base URL (default: http://localhost:8080)
  --token TOKEN               admin access token (default: super_token)
  --file-date DATE            yyyy-mm-dd fallback for records without their own timestamp
  -h, --help                  this text

Example:
  ./backfill-download-logs.sh --count 500 --days 14
USAGE
}

while [ $# -gt 0 ]; do
    case "$1" in
        --format) FORMAT="$2"; shift 2 ;;
        --count) COUNT="$2"; shift 2 ;;
        --days) DAYS="$2"; shift 2 ;;
        --storage-type) STORAGE_TYPE="$2"; shift 2 ;;
        --registry-url) REGISTRY_URL="$2"; shift 2 ;;
        --token) TOKEN="$2"; shift 2 ;;
        --file-date) FILE_DATE="$2"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "unknown option: $1" >&2; usage >&2; exit 2 ;;
    esac
done

WORK=$(mktemp -d -t ovsx-backfill-XXXXXX)
trap 'rm -rf "${WORK}"' EXIT
LOG_FILE="${WORK}/access.log"
RESPONSE_FILE="${WORK}/response.json"

"${GENERATE}" --format "${FORMAT}" --count "${COUNT}" --days "${DAYS}" --storage-type "${STORAGE_TYPE}" \
    --out "${LOG_FILE}"

# identifies the upload to the backfill ledger, so re-running this script twice would be rejected
# as a duplicate rather than double-counting - vary it per run
FILE_NAME="manual-$(date -u +%Y%m%d-%H%M%S)-$$-${FORMAT}.log"

URL="${REGISTRY_URL}/admin/api/analytics/downloads/backfill?token=${TOKEN}&fileName=${FILE_NAME}&format=${FORMAT}"
if [ -n "${FILE_DATE}" ]; then
    URL="${URL}&fileDate=${FILE_DATE}"
fi

echo "backfilling ${COUNT} ${FORMAT} download line(s) as '${FILE_NAME}'..." >&2
# the parser auto-detects gzip from content, so the plain file the generator wrote needs no
# compression step here
HTTP_CODE=$(curl -sS -o "${RESPONSE_FILE}" -w '%{http_code}' -X POST "${URL}" \
    -H 'Content-Type: application/octet-stream' --data-binary @"${LOG_FILE}")

cat "${RESPONSE_FILE}"
echo

if [ "${HTTP_CODE}" != "200" ]; then
    echo "backfill request failed with HTTP ${HTTP_CODE}" >&2
    exit 1
fi
