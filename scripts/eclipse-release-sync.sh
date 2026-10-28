#!/usr/bin/env bash
# Creates the Eclipse project releases that exist on GitHub but not on projects.eclipse.org.
# Dry run by default; --apply submits the form. Optional VERSION args limit what is created.
# Needs ~/.eclipse-cookie (chmod 600) holding the browser's Cookie header value (SSESS...=...).
# Usage: ./eclipse-release-sync.sh [--apply] [VERSION...]
set -euo pipefail

BASE="https://projects.eclipse.org/projects/ecd.openvsx"
COOKIE_FILE=${ECLIPSE_COOKIE_FILE:-$HOME/.eclipse-cookie}
DIFF="$(dirname "$0")/eclipse-release-diff.sh"

apply=0
[ "${1:-}" = "--apply" ] && { apply=1; shift; }
only=("$@")

[ -r "$COOKIE_FILE" ] || { echo "missing $COOKIE_FILE" >&2; exit 1; }
cookie=$(<"$COOKIE_FILE")

while IFS=$'\t' read -r ver date _; do
  if [ ${#only[@]} -gt 0 ] && ! printf '%s\n' "${only[@]}" | grep -qxF "$ver"; then continue; fi
  # The overview page can be cached; the per-version page is the source of truth. The site caches
  # a 404 for 15 minutes, so every probe carries a unique query string to bypass it.
  if curl -fsS -o /dev/null "$BASE/releases/$ver?_=$RANDOM$RANDOM" 2>/dev/null; then echo "exists, skipping: $ver"; continue; fi
  if [ $apply -eq 0 ]; then echo "would create: $ver ($date)"; continue; fi

  form=$(curl -fsSL -H "Cookie: $cookie" "$BASE/create-release")
  field() { grep -oP "name=\"$1\" value=\"\K[^\"]+" <<<"$form" | head -1; }
  build_id=$(field form_build_id); token=$(field form_token); form_id=$(field form_id); project=$(field project)
  [ -n "$build_id" ] && [ -n "$token" ] || { echo "form not found: cookie expired or no permission" >&2; exit 1; }

  curl -fsSL -o /dev/null -H "Cookie: $cookie" "$BASE/create-release" \
    --data-urlencode "project=$project" --data-urlencode "date=$date" --data-urlencode "name=$ver" \
    --data-urlencode "form_build_id=$build_id" --data-urlencode "form_token=$token" \
    --data-urlencode "form_id=$form_id" --data-urlencode "op=Create"

  # The release page existing is the only success signal the form gives.
  if curl -fsS -o /dev/null "$BASE/releases/$ver?_=$RANDOM$RANDOM"; then echo "created: $ver ($date)"
  else echo "FAILED (no release page): $ver" >&2; exit 1; fi
done < <("$DIFF" --tsv)
