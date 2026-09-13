#!/usr/bin/env bash

# Generates access-log files for the download ingestion pipeline and, optionally, uploads them to
# the local MinIO bucket the AWS source reads.
#
# A record is only counted when it survives two filters:
#   1. AccessLogRecord#isVsixDownload - GET, status 200, URL ending in .vsix
#   2. DownloadIngestionProcessor#resolveExtensions - the .vsix filename (uppercased) must match a
#      file_resource row of type 'download' whose storage_type equals the source's storage type
# So the filenames come from the registry database rather than being invented.
set -euo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
SERVER_ROOT=$( dirname "${SCRIPT_DIR}" )
REPO_ROOT=$( dirname "${SERVER_ROOT}" )

# docker compose resolves its project from the working directory, so pin it to the compose file
# rather than requiring the caller to be in the repo root.
compose() {
    docker compose -f "${REPO_ROOT}/docker-compose.yml" "$@"
}

FORMAT=cloudfront
COUNT=200
DAYS=7
BUCKET="test"
PREFIX=AWSLogs/
STORAGE_TYPE=aws
OUT=""
UPLOAD=0
HOST=openvsx.example.org

usage() {
    cat <<'USAGE'
Usage: generate-download-logs.sh [options]

  --format cloudfront|fastly  log format to emit (default: cloudfront)
  --count N                   number of download lines (default: 200)
  --days N                    spread timestamps over the last N days (default: 7)
  --bucket NAME               MinIO bucket (default: test)
  --prefix PREFIX             key prefix, must match ovsx.logs.aws.log-location-prefix (default: AWSLogs/)
  --storage-type TYPE         file_resource.storage_type to draw filenames from (default: aws)
  --out FILE                  write the (uncompressed) log here instead of a temp file
  --upload                    gzip and upload to the bucket via the minio container
  -h, --help                  this text

Examples:
  # see what would be generated
  ./generate-download-logs.sh --count 20 --out /dev/stdout

  # 500 downloads over 14 days, uploaded so the next ingestion run picks them up
  ./generate-download-logs.sh --count 500 --days 14 --upload
USAGE
}

while [ $# -gt 0 ]; do
    case "$1" in
        --format) FORMAT="$2"; shift 2 ;;
        --count) COUNT="$2"; shift 2 ;;
        --days) DAYS="$2"; shift 2 ;;
        --bucket) BUCKET="$2"; shift 2 ;;
        --prefix) PREFIX="$2"; shift 2 ;;
        --storage-type) STORAGE_TYPE="$2"; shift 2 ;;
        --out) OUT="$2"; shift 2 ;;
        --upload) UPLOAD=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "unknown option: $1" >&2; usage >&2; exit 2 ;;
    esac
done

case "$FORMAT" in
    cloudfront|fastly) ;;
    *) echo "--format must be cloudfront or fastly" >&2; exit 2 ;;
esac

# --- the filenames the processor will be able to resolve -------------------------------------
# Joined up to extension_version so the URL path can carry the real namespace/name/version, which
# is what a production log looks like. The processor only reads the last segment, but a realistic
# path makes the generated file readable and keeps the Fastly 'url' field honest.
QUERY="
SELECT fr.name, n.name, e.name, ev.version
FROM file_resource fr
JOIN extension_version ev ON ev.id = fr.extension_id
JOIN extension e ON e.id = ev.extension_id
JOIN namespace n ON n.id = e.namespace_id
WHERE fr.type = 'download' AND fr.storage_type = '${STORAGE_TYPE}'
ORDER BY fr.name;"

mapfile -t ROWS < <(compose exec -T postgres psql -U openvsx -d postgres -At -F'|' -c "$QUERY")

if [ "${#ROWS[@]}" -eq 0 ]; then
    cat >&2 <<EOF
No file_resource rows of type 'download' with storage_type '${STORAGE_TYPE}'.

Publish at least one extension first, and make sure the server stores files under that backend -
for '${STORAGE_TYPE}'=aws that means the ovsx.storage.aws.* block in src/dev/resources/application.yml
(the commented MinIO example) and 'docker compose --profile minio up'.
EOF
    exit 1
fi

echo "drawing from ${#ROWS[@]} published .vsix file(s) with storage_type='${STORAGE_TYPE}'" >&2

LOG_FILE="${OUT:-$(mktemp -t ovsx-downloads-XXXXXX.log)}"

AGENTS=(
    "VSCode%201.90.2%20(Microsoft%20Visual%20Studio%20Code)"
    "VSCode%201.95.0%20(Microsoft%20Visual%20Studio%20Code)"
    "VSCodium%201.93.1"
    "Mozilla/5.0%20(X11;%20Linux%20x86_64)"
)
COUNTRIES=(germany "united states" france japan brazil)
NOW=$(date -u +%s)
WINDOW=$((DAYS * 86400))

emit_cloudfront() {
    # 33 tab-separated fields, matching AWS standard access logs. CloudFrontLogFileParser splits on
    # [ \t]+ and reads: [0] date [1] time [4] c-ip [5] method [7] uri-stem [8] status [10] user-agent
    printf '#Version: 1.0\n'
    printf '#Fields: date\ttime\tx-edge-location\tsc-bytes\tc-ip\tcs-method\tcs(Host)\tcs-uri-stem\tsc-status\tcs(Referer)\tcs(User-Agent)\tcs-uri-query\tcs(Cookie)\tx-edge-result-type\tx-edge-request-id\tx-host-header\tcs-protocol\tcs-bytes\ttime-taken\tx-forwarded-for\tssl-protocol\tssl-cipher\tx-edge-response-result-type\tcs-protocol-version\tfle-status\tfle-encrypted-fields\tc-port\ttime-to-first-byte\tx-edge-detailed-result-type\tsc-content-type\tsc-content-len\tsc-range-start\tsc-range-end\n'
    for i in $(seq 1 "$COUNT"); do
        row="${ROWS[$((RANDOM % ${#ROWS[@]}))]}"
        IFS='|' read -r fname ns ext ver <<<"$row"
        when=$((NOW - RANDOM * WINDOW / 32767))
        d=$(date -u -d "@$when" +%Y-%m-%d)
        t=$(date -u -d "@$when" +%H:%M:%S)
        ip="1.$((RANDOM % 254 + 1)).$((RANDOM % 254 + 1)).$((RANDOM % 254 + 1))"
        ua="${AGENTS[$((RANDOM % ${#AGENTS[@]}))]}"
        uri="/${ns}/${ext}/${ver}/file/${fname}"
        printf '%s\t%s\tLHR61-P5\t1234567\t%s\tGET\tabcd.cloudfront.net\t%s\t200\t-\t%s\t-\t-\tHit\treq%s\t%s\thttps\t66\t0.944\t-\tTLSv1.3\tTLS_AES_128_GCM_SHA256\tHit\tHTTP/2.0\t-\t-\t61198\t0.144\tHit\tapplication/octet-stream\t1234567\t-\t-\n' \
            "$d" "$t" "$ip" "$uri" "$ua" "$i" "$HOST"
    done
    # A little noise, so the run exercises the skip paths rather than only the happy one.
    printf '%s\t%s\tLHR61-P5\t380\t1.1.1.1\tOPTIONS\tabcd.cloudfront.net\t/vscjava/vscode-java-pack/0.30.4/package.json\t200\t-\tMozilla/5.0\t-\t-\tMiss\treqx\t%s\thttps\t66\t0.044\t-\tTLSv1.3\tTLS_AES_128_GCM_SHA256\tMiss\tHTTP/2.0\t-\t-\t61198\t0.044\tMiss\t-\t0\t-\t-\n' \
        "$(date -u +%Y-%m-%d)" "$(date -u +%H:%M:%S)" "$HOST"
    printf 'garbage line\n'
}

emit_fastly() {
    # Syslog framing plus a JSON payload, as the Fastly S3 log stream writes it.
    for i in $(seq 1 "$COUNT"); do
        row="${ROWS[$((RANDOM % ${#ROWS[@]}))]}"
        IFS='|' read -r fname ns ext ver <<<"$row"
        when=$((NOW - RANDOM * WINDOW / 32767))
        stamp=$(date -u -d "@$when" +%Y-%m-%dT%H:%M:%S+0000)
        frame=$(date -u -d "@$when" +%Y-%m-%dT%H:%M:%SZ)
        ip="1.$((RANDOM % 254 + 1)).$((RANDOM % 254 + 1)).$((RANDOM % 254 + 1))"
        country="${COUNTRIES[$((RANDOM % ${#COUNTRIES[@]}))]}"
        printf '<134>%s cache-fra-eddf8230176 S3-Log-Stream-test[%s]: {"timestamp": "%s", "client_ip": "%s", "geo_country": "%s", "geo_city": "somewhere","host": "%s","url": "/%s/%s/%s/file/%s","request_method": "GET", "request_protocol": "HTTP/1.1", "request_referer": "", "request_user_agent": "VSCode 1.90.2 (Microsoft Visual Studio Code)", "response_state": "HIT", "response_status": 200, "response_reason": "OK", "response_body_size": 1234567,"fastly_server": "xxxx", "fastly_is_edge": true}\n' \
            "$frame" "$i" "$stamp" "$ip" "$country" "$HOST" "$ns" "$ext" "$ver" "$fname"
    done
    printf '<134>%s cache-fra-eddf8230176 S3-Log-Stream-test[999]: {"host": "%s","url": "/favicon.ico","request_method": "GET", "response_status": 301}\n' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$HOST"
    printf 'plain text line without any json payload\n'
}

if [ "$FORMAT" = cloudfront ]; then emit_cloudfront > "$LOG_FILE"; else emit_fastly > "$LOG_FILE"; fi

if [ -z "$OUT" ] || [ "$OUT" != /dev/stdout ]; then
    echo "wrote $COUNT download line(s) to $LOG_FILE" >&2
fi

if [ "$UPLOAD" -eq 1 ]; then
    # Only .gz keys under the prefix are listed by AwsDownloadRecordSource.
    key="${PREFIX}$(date -u +%Y%m%d-%H%M%S)-${FORMAT}.gz"
    gzip -c "$LOG_FILE" | compose exec -T minio sh -c "
        mc alias set local http://localhost:9000 minioadmin minioadmin >/dev/null &&
        mc pipe 'local/${BUCKET}/${key}'" >/dev/null
    echo "uploaded s3://${BUCKET}/${key}" >&2
fi
