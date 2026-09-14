#!/usr/bin/env bash

# Backfills download_event from access logs held in Grafana Cloud Logs (Loki), for a registry that
# enabled download analytics after the fact: the series is read from the download_stats_daily
# aggregate, so every day before the first ingested event reads as zero rather than as missing.
#
# It deliberately does NOT replay the logs through the ingestion pipeline. DownloadIngestionProcessor
# increments extension.download_count in the same transaction as it writes the events, so a replay
# would add the backfilled period to every lifetime total a second time. Writing download_event
# directly leaves the registry's counters alone.
#
# The filter and the resolution mirror the server, so a backfilled row is the row the live pipeline
# would have written:
#   1. AccessLogRecord#isVsixDownload      - GET, status 200, URL ending in .vsix
#   2. AccessLogRecord#toRawDownloadRecord - the last URL segment, percent-decoded and uppercased
#   3. DownloadIngestionProcessor#resolveExtensions - that name must match a file_resource row of
#      type 'download' whose storage_type equals the one being backfilled
# Downloads whose filename resolves to nothing are counted and dropped, as the server drops them.
#
# Usage: backfill-download-events.sh --from 2026-01-01 --to 2026-04-01 [options]
set -euo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

FROM=""
TO=""
FORMAT=cloudfront
SELECTOR=""
QUERY=""
STORAGE_TYPE=aws
BUCKET=hour
OUT=""
APPLY=0
REFRESH=0
LIMIT=5000
REGISTRY_URL="${OVSX_REGISTRY_URL:-}"
TIMESERIES_URL="${OVSX_TIMESERIES_URL:-}"
LOKI_URL="${GRAFANA_LOGS_URL:-}"
LOKI_USER="${GRAFANA_LOGS_USER:-}"
LOKI_TOKEN="${GRAFANA_LOGS_TOKEN:-}"

usage() {
    cat <<'USAGE'
Usage: backfill-download-events.sh --from DATE --to DATE [options]

  --from DATE                 first UTC day to backfill, inclusive (YYYY-MM-DD)
  --to DATE                   last UTC day, exclusive (YYYY-MM-DD)
  --format cloudfront|fastly  log format stored in Loki (default: cloudfront)
  --selector SELECTOR         LogQL stream selector, e.g. '{job="cloudfront"}'
  --query QUERY               full LogQL query, replacing --selector and its .vsix line filter
  --storage-type TYPE         file_resource.storage_type to resolve against (default: aws)
  --bucket hour|day           timestamp granularity of the written events (default: hour)
  --out FILE                  write the events here (default: ./download-events-FROM_TO.tsv)
  --apply                     load the events into download_event; without it nothing is written
  --refresh                   after loading, materialize the range into download_stats_daily
  --limit N                   entries per Loki request (default: 5000)
  -h, --help                  this text

Connections, as environment variables or options:
  GRAFANA_LOGS_URL    --loki-url        e.g. https://logs-prod-012.grafana.net
  GRAFANA_LOGS_USER   --loki-user       Grafana Cloud Logs user id
  GRAFANA_LOGS_TOKEN  --loki-token      access policy token with logs:read
  OVSX_REGISTRY_URL   --registry-url    libpq URL of the registry database, read only
  OVSX_TIMESERIES_URL --timeseries-url  libpq URL of the time-series database

Examples:
  # see what a week would produce, writing nothing
  ./backfill-download-events.sh --from 2026-01-01 --to 2026-01-08 --selector '{job="cloudfront"}'

  # backfill a quarter and materialize it, so the graph shows it rather than the aggregate's zeros
  ./backfill-download-events.sh --from 2026-01-01 --to 2026-04-01 \
      --selector '{job="cloudfront"}' --apply --refresh
USAGE
}

while [ $# -gt 0 ]; do
    case "$1" in
        --from) FROM="$2"; shift 2 ;;
        --to) TO="$2"; shift 2 ;;
        --format) FORMAT="$2"; shift 2 ;;
        --selector) SELECTOR="$2"; shift 2 ;;
        --query) QUERY="$2"; shift 2 ;;
        --storage-type) STORAGE_TYPE="$2"; shift 2 ;;
        --bucket) BUCKET="$2"; shift 2 ;;
        --out) OUT="$2"; shift 2 ;;
        --apply) APPLY=1; shift ;;
        --refresh) REFRESH=1; shift ;;
        --limit) LIMIT="$2"; shift 2 ;;
        --loki-url) LOKI_URL="$2"; shift 2 ;;
        --loki-user) LOKI_USER="$2"; shift 2 ;;
        --loki-token) LOKI_TOKEN="$2"; shift 2 ;;
        --registry-url) REGISTRY_URL="$2"; shift 2 ;;
        --timeseries-url) TIMESERIES_URL="$2"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "unknown option: $1" >&2; usage >&2; exit 2 ;;
    esac
done

fail() { echo "$@" >&2; exit 2; }

[ -n "$FROM" ] && [ -n "$TO" ] || fail "--from and --to are required"
case "$FORMAT" in cloudfront|fastly) ;; *) fail "--format must be cloudfront or fastly" ;; esac
case "$BUCKET" in hour|day) ;; *) fail "--bucket must be hour or day" ;; esac
[ -n "$QUERY" ] || [ -n "$SELECTOR" ] || fail "--selector or --query is required"
[ -n "$LOKI_URL" ] || fail "GRAFANA_LOGS_URL or --loki-url is required"
[ -n "$REGISTRY_URL" ] || fail "OVSX_REGISTRY_URL or --registry-url is required"
if [ "$APPLY" -eq 1 ] || [ "$REFRESH" -eq 1 ]; then
    [ -n "$TIMESERIES_URL" ] || fail "OVSX_TIMESERIES_URL or --timeseries-url is required with --apply"
fi
for tool in curl jq psql awk date; do
    command -v "$tool" >/dev/null || fail "$tool is required but not on PATH"
done

date -u -d "${FROM}T00:00:00Z" +%s >/dev/null 2>&1 || fail "--from is not a date: ${FROM}"
date -u -d "${TO}T00:00:00Z" +%s >/dev/null 2>&1 || fail "--to is not a date: ${TO}"
START_EPOCH=$( date -u -d "${FROM}T00:00:00Z" +%s )
END_EPOCH=$( date -u -d "${TO}T00:00:00Z" +%s )
[ "$START_EPOCH" -lt "$END_EPOCH" ] || fail "--from must be before --to"

# The line filter cuts the transferred volume by orders of magnitude and costs nothing: a download
# only counts when its URL ends in .vsix, so a line without that substring cannot contribute.
[ -n "$QUERY" ] || QUERY="${SELECTOR} |= \".vsix\""

WORK=$( mktemp -d )
trap 'rm -rf "${WORK}"' EXIT

CURL_AUTH=()
[ -z "$LOKI_USER" ] || CURL_AUTH=(--user "${LOKI_USER}:${LOKI_TOKEN}")

# --- the log lines ------------------------------------------------------------------------------

# Loki answers at most --limit entries per request, so each day is paged through with
# direction=forward, restarting one nanosecond after the last entry received. A day per query also
# keeps every request inside Loki's max_query_length.
fetch_day() {
    local cursor_ns=$1 end_ns=$2 received last_ns
    while : ; do
        curl --fail --silent --show-error --get "${LOKI_URL}/loki/api/v1/query_range" \
            "${CURL_AUTH[@]}" \
            --data-urlencode "query=${QUERY}" \
            --data-urlencode "start=${cursor_ns}" \
            --data-urlencode "end=${end_ns}" \
            --data-urlencode "limit=${LIMIT}" \
            --data-urlencode "direction=forward" \
            --output "${WORK}/response.json"

        # [timestamp, line] pairs across every stream, oldest first: one file to parse, and the
        # last timestamp is where the next page starts.
        jq -r '[.data.result[]?.values[]?] | sort_by(.[0] | tonumber) | .[] | "\(.[0])\t\(.[1])"' \
            "${WORK}/response.json" > "${WORK}/page.tsv"

        received=$( wc -l < "${WORK}/page.tsv" | tr -d ' ' )
        [ "${received}" -gt 0 ] || break

        cut -f2- "${WORK}/page.tsv" >> "${WORK}/lines.log"
        [ "${received}" -ge "${LIMIT}" ] || break

        last_ns=$( tail -n 1 "${WORK}/page.tsv" | cut -f1 )
        cursor_ns=$(( last_ns + 1 ))
        [ "${cursor_ns}" -lt "${end_ns}" ] || break
    done
}

: > "${WORK}/lines.log"
echo "querying ${LOKI_URL} for ${FROM}..${TO}" >&2
day=$START_EPOCH
while [ "$day" -lt "$END_EPOCH" ]; do
    next=$(( day + 86400 ))
    [ "$next" -le "$END_EPOCH" ] || next=$END_EPOCH
    fetch_day "${day}000000000" "${next}000000000"
    printf '  %s: %s line(s) so far\n' "$( date -u -d "@${day}" +%Y-%m-%d )" \
        "$( wc -l < "${WORK}/lines.log" | tr -d ' ' )" >&2
    day=$next
done

# --- (bucket, filename, country) triples, filtered the way the server filters ---------------------

# The filename is the last URL segment, percent-decoded and uppercased, exactly as
# AccessLogRecord#toRawDownloadRecord builds it - the resolution below matches on that form.
# Written for POSIX awk rather than gawk, so it runs wherever mawk is the default.
AWK_COMMON='
    function urldecode(value,   out, i, c, hex, high, low) {
        out = ""
        i = 1
        while (i <= length(value)) {
            c = substr(value, i, 1)
            if (c == "%" && i + 2 <= length(value)) {
                hex = toupper(substr(value, i + 1, 2))
                high = index("0123456789ABCDEF", substr(hex, 1, 1)) - 1
                low = index("0123456789ABCDEF", substr(hex, 2, 1)) - 1
                if (high >= 0 && low >= 0) {
                    out = out sprintf("%c", high * 16 + low)
                    i += 3
                    continue
                }
            }
            out = out c
            i++
        }
        return out
    }
    function filename(url,   parts, count) {
        count = split(url, parts, "/")
        return toupper(urldecode(parts[count]))
    }
    # The timestamps are already UTC calendar text, so the bucket is a substring of them rather
    # than epoch arithmetic - which keeps this clear of gawk s mktime/strftime.
    function bucket(date, hour) {
        return BUCKET == "day" ? date " 00:00:00+00" : date " " hour ":00:00+00"
    }
    # download_event.country is CHAR(2), and CountryCodes accepts either an ISO code or an English
    # country name. An empty result means "unknown", which is what the column holds for CloudFront.
    function country(value,   lower) {
        if (value == "") return ""
        if (value ~ /^[A-Za-z][A-Za-z]$/) return toupper(value)
        lower = tolower(value)
        if (lower in iso) return iso[lower]
        unmapped++
        return ""
    }
'

# Fastly names a country in full and download_event.country is an ISO code, so the names are mapped
# the way CountryCodes maps them server-side: out of java.util.Locale rather than from a table kept
# here, so a backfilled row carries the country the live ingestion would have stored.
: > "${WORK}/countries.tsv"
if [ "$FORMAT" = fastly ]; then
    if command -v java >/dev/null; then
        java "${SCRIPT_DIR}/src/CountryCodesTable.java" > "${WORK}/countries.tsv"
    else
        echo "java is not on PATH: country names cannot be mapped to ISO codes and are left unset" >&2
    fi
fi

if [ "$FORMAT" = cloudfront ]; then
    # Tab- or space-separated, per CloudFrontLogFileParser: [1] date [2] time [6] method
    # [8] uri-stem [9] status, counting from one as awk does. CloudFront carries no country.
    awk -v BUCKET="$BUCKET" "${AWK_COMMON}"'
        /^#/ { next }
        {
            n = split($0, f, /[ \t]+/)
            if (n < 11) next
            if (toupper(f[6]) != "GET" || f[9] != "200") next
            if (f[8] !~ /\.vsix$/) next
            if (f[1] !~ /^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]$/) next
            print bucket(f[1], substr(f[2], 1, 2)) "\t" filename(f[8]) "\t"
        }
    ' "${WORK}/lines.log" > "${WORK}/records.tsv"
else
    # Syslog framing plus a JSON payload, per FastlyLogFileParser: everything from the first brace.
    # fromjson? drops a line that is not JSON, the way the server logs and skips it.
    sed -n 's/^[^{]*\({.*\)$/\1/p' "${WORK}/lines.log" \
        | jq -R -r '
            fromjson? // empty
            | select(.request_method != null and (.request_method | ascii_upcase) == "GET")
            | select(.response_status == 200)
            | select(.url != null and (.url | endswith(".vsix")))
            | [(.timestamp // ""), .url, (.geo_country // "")] | @tsv' \
        | awk -v BUCKET="$BUCKET" -F'\t' "${AWK_COMMON}"'
            # the country table first, then the records on stdin
            NR == FNR { iso[$1] = $2; next }
            {
                # 2026-02-09T04:20:50+0000: only UTC is handled, and anything else is counted
                # rather than silently filed under the wrong day.
                if ($1 !~ /^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:/) { malformed++; next }
                if (substr($1, 20) != "+0000" && substr($1, 20) != "Z") { nonutc++; next }
                print bucket(substr($1, 1, 10), substr($1, 12, 2)) "\t" filename($2) "\t" country($3)
            }
            END {
                if (malformed > 0) printf "skipped %d record(s) with an unreadable timestamp\n", malformed > "/dev/stderr"
                if (nonutc > 0) printf "skipped %d record(s) whose timestamp is not UTC\n", nonutc > "/dev/stderr"
                if (unmapped > 0) printf "%d record(s) name a country no ISO code was found for\n", unmapped > "/dev/stderr"
            }
        ' "${WORK}/countries.tsv" - > "${WORK}/records.tsv"
fi

# Counting here is what makes a backfill cheap: one row per bucket and file rather than per request.
# Keyed on the whole line rather than counted with uniq -c, which would have to be unpicked from a
# leading count column - and rebuilding the line after that turns its tabs into spaces.
awk -F'\t' '{ counted[$0]++ } END { for (key in counted) print key "\t" counted[key] }' \
    "${WORK}/records.tsv" | sort > "${WORK}/counted.tsv"

printf '%s log line(s) read, %s download(s), %s bucket(s)\n' \
    "$( wc -l < "${WORK}/lines.log" | tr -d ' ' )" \
    "$( wc -l < "${WORK}/records.tsv" | tr -d ' ' )" \
    "$( wc -l < "${WORK}/counted.tsv" | tr -d ' ' )" >&2

# --- resolution against the registry --------------------------------------------------------------

# The same join as the server's resolveExtensions, on the uppercased file name. file_resource's
# extension_id column references extension_version, which the alias spells out. The query goes in on
# stdin because psql only interpolates variables there and with -f, never in a -c argument.
psql "${REGISTRY_URL}" -At -F$'\t' -v storage_type="${STORAGE_TYPE}" <<'SQL' > "${WORK}/resolution.tsv"
SELECT upper(fr.name), e.id, ev.id, n.name, e.name, ev.version,
       coalesce(ev.target_platform, 'universal')
FROM file_resource fr
JOIN extension_version ev ON ev.id = fr.extension_id
JOIN extension e ON e.id = ev.extension_id
JOIN namespace n ON n.id = e.namespace_id
WHERE fr.type = 'download' AND fr.storage_type = :'storage_type';
SQL

[ -s "${WORK}/resolution.tsv" ] || fail "no file_resource rows of type 'download' with storage_type '${STORAGE_TYPE}'"

# --- events ----------------------------------------------------------------------------------------

EVENTS="${OUT:-$( pwd )/download-events-${FROM}_${TO}.tsv}"
awk -F'\t' -v OFS='\t' '
    NR == FNR { id[$1] = $2; version_id[$1] = $3; ns[$1] = $4; name[$1] = $5; v[$1] = $6; tp[$1] = $7; next }
    {
        if (!($2 in id)) { unresolved[$2] += $4; next }
        print $1, id[$2], version_id[$2], ns[$2], name[$2], v[$2], tp[$2], ($3 == "" ? "\\N" : $3), $4
    }
    END {
        for (file in unresolved) { downloads += unresolved[file]; files++ }
        if (files > 0) {
            printf "skipped %d download(s) referring to %d unknown vsix file(s)\n", downloads, files > "/dev/stderr"
        }
    }
' "${WORK}/resolution.tsv" "${WORK}/counted.tsv" > "${EVENTS}"

printf '%s event row(s), %s download(s) in total, written to %s\n' \
    "$( wc -l < "${EVENTS}" | tr -d ' ' )" \
    "$( awk -F'\t' '{ sum += $9 } END { print sum + 0 }' "${EVENTS}" )" \
    "${EVENTS}" >&2

if [ "$APPLY" -eq 0 ]; then
    echo "nothing loaded; review the file and pass --apply to write it to download_event" >&2
    exit 0
fi

psql "${TIMESERIES_URL}" -v ON_ERROR_STOP=1 -c "\\copy download_event (time, extension_id, extension_version_id, namespace, extension_name, version, target_platform, country, count) FROM '${EVENTS}'"

if [ "$REFRESH" -eq 1 ]; then
    # The refresh policy's start_offset is 90 days, so it never materializes a backfill older than
    # that, and the series is read from the aggregate. A manual refresh has no such bound. Do it
    # before the retention policy next drops the raw rows: the aggregate is kept, download_event is
    # not. Not in a transaction, which refresh_continuous_aggregate does not allow.
    echo "materializing ${FROM}..${TO} into download_stats_daily" >&2
    psql "${TIMESERIES_URL}" -v ON_ERROR_STOP=1 \
        -c "CALL refresh_continuous_aggregate('download_stats_daily', '${FROM}'::timestamptz, '${TO}'::timestamptz);"
    echo "done; responses stay cached for up to ovsx.analytics.settled-cache.ttl (default PT1H) per node" >&2
else
    cat >&2 <<EOF

Loaded, but not yet visible: the series is read from download_stats_daily, and the refresh policy
only materializes the last 90 days. Run this before the retention policy drops the raw rows:

  psql "\$OVSX_TIMESERIES_URL" -c "CALL refresh_continuous_aggregate('download_stats_daily', '${FROM}', '${TO}');"

Responses then stay cached for up to ovsx.analytics.settled-cache.ttl (default PT1H) per node.
EOF
fi
