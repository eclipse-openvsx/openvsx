#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
PROJECT_ROOT=$( dirname "${SCRIPT_DIR}" )

usage() {
  local USAGE
  USAGE="
Usage: $(basename "${0}") <previous-tag>

Prints the release notes of an Open VSX release: the server and web UI change log sections added
since <previous-tag>, the previous Open VSX release (e.g. v1.2.0). The web UI belongs in them
because the release builds its image from the same commit, released to npm or not.
"
  echo "$USAGE"
  exit 1
}

[ $# -eq 1 ] || usage
PREVIOUS_TAG=$1
cd "${PROJECT_ROOT}"
git rev-parse --verify --quiet "${PREVIOUS_TAG}^{commit}" > /dev/null || { echo "Unknown tag '${PREVIOUS_TAG}'." >&2; exit 1; }

# Prints the sections of <file> above the previous release: the previous tag's own version, or the
# newest release the file already held at that tag. Empty sections, such as a freshly opened [next],
# are skipped. A single section prints as its subsections; several keep their version headings.
sections() {
  local file=$1 held
  held=$(git show "${PREVIOUS_TAG}:${file}" 2> /dev/null | grep -m1 -o '^#* \[v[^]]*\]' | grep -o '\[v[^]]*\]' || true)
  awk -v previous="[${PREVIOUS_TAG}]" -v held="${held}" '
    /^##+ \[/ {
      version = $2
      if (version == previous || version == held) { exit }
      sub(/^#+ /, "")
      n++; heading[n] = $0; next
    }
    n { body[n] = body[n] $0 "\n" }
    END {
      for (i = 1; i <= n; i++) {
        if (body[i] ~ /[^[:space:]]/) { kept[++k] = i }
      }
      if (k == 0) { print "No changes."; exit }
      for (j = 1; j <= k; j++) {
        text = body[kept[j]]
        if (k > 1) {
          gsub(/\n### /, "\n#### ", text)
          sub(/^### /, "#### ", text)
          printf "### %s\n\n", heading[kept[j]]
        }
        gsub(/^\n+|\n+$/, "", text)
        printf "%s\n\n", text
      }
    }' "${file}"
}

printf '## Server\n\n'
sections server/CHANGELOG.md
printf '## Web UI\n\n'
sections webui/CHANGELOG.md
