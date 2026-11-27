#!/usr/bin/env bash
# Prints site/release.json: the newest stable and nightly APK, read once at deploy time.
# The download page reads this file instead of the GitHub API, because the API allows an
# anonymous visitor 60 requests an hour per IP and many visitors share one IP.
# Usage: GH_TOKEN=… tools/site_release_json.sh [owner/repo] > site/release.json
set -euo pipefail
repo="${1:-${GITHUB_REPOSITORY:-AfshinJalili/Messages}}"

# One release → the fields the page shows. The version is the tag without "v" and suffix:
# v1.8.0-414 → 1.8.0, v1.8.0-nightly.20261125.2 → 1.8.0.
pick='if . == null then null else {
  version: (.tag_name | ltrimstr("v") | split("-")[0]),
  tag: .tag_name,
  published: .published_at,
  page: .html_url,
  apk: ([.assets[] | select(.name | endswith(".apk"))][0]
        | if . == null then null else {
            name, size, url: .browser_download_url,
            sha256: ((.digest // "") | ltrimstr("sha256:"))
          } end)
} end'

stable=$(gh api "repos/$repo/releases/latest" 2>/dev/null | jq "$pick" || echo null)
nightly=$(gh api "repos/$repo/releases?per_page=30" \
  | jq "[.[] | select(.draft | not) | select(.tag_name | test(\"-nightly\\\\.\"))][0] | $pick")

jq -n --argjson stable "${stable:-null}" --argjson nightly "${nightly:-null}" --arg repo "$repo" '{
  generated: (now | todate),
  releases: "https://github.com/\($repo)/releases",
  stable: $stable,
  nightly: $nightly
}'
