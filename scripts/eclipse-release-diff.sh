#!/usr/bin/env bash
# Lists server releases (v* tags) on GitHub that are missing from the Eclipse project page.
# Needs: gh (authenticated or public access), curl. Read-only; creates nothing.
# Usage: ./eclipse-release-diff.sh [--tsv]
set -euo pipefail

REPO="eclipse-openvsx/openvsx"
PAGE="https://projects.eclipse.org/projects/ecd.openvsx"

eclipse=$(curl -fsSL "$PAGE" | grep -oE '/releases/[0-9][^"]*"' | sed 's#/releases/##;s#"##' | sort -u)
[ -n "$eclipse" ] || { echo "no releases parsed from $PAGE (page layout changed?)" >&2; exit 1; }

# shortcut: --limit 500 covers the history, raise if releases ever exceed it
gh release list -R "$REPO" --limit 500 --json tagName,publishedAt,isDraft,isPrerelease \
  -q '.[] | select(.isDraft|not) | select(.tagName|test("^v[0-9]")) | [.tagName[1:], .publishedAt[:10], .tagName] | @tsv' |
sort -V |
while IFS=$'\t' read -r ver date tag; do
  grep -qxF "$ver" <<<"$eclipse" && continue
  # The project page is cached for anonymous readers and ignores a query string; the release page
  # honours one, so it settles whether a release the listing lacks really is missing.
  curl -fsS -o /dev/null "$PAGE/releases/$ver?_=$RANDOM$RANDOM" 2>/dev/null && continue
  if [ "${1:-}" = "--tsv" ]; then
    printf '%s\t%s\thttps://github.com/%s/releases/tag/%s\n' "$ver" "$date" "$REPO" "$tag"
  else
    printf -- '- [ ] %s  (%s)  https://github.com/%s/releases/tag/%s\n' "$ver" "$date" "$REPO" "$tag"
  fi
done
