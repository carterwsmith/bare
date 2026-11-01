#!/usr/bin/env bash
# Download an APK and install it on a Fire TV over ADB.
set -euo pipefail

usage() {
  printf 'Usage: %s <apk-url> <tv-address>\n' "$0"
  printf '%s\n' \
    '' \
    'Download a direct HTTP(S) APK URL and install it over ADB.' \
    'The TV address is required; the ADB port defaults to 5555.' \
    'ADB debugging must be enabled, and this Mac must be authorized on the TV.' \
    'Existing apps are updated without clearing their data.'
}

if [[ "${1:-}" == --help || "${1:-}" == -h ]]; then
  usage
  exit 0
fi

if [[ $# -ne 2 ]]; then
  usage >&2
  exit 1
fi

URL="$1"
TV="$2"
case "$URL" in
  http://*|https://*) ;;
  *) printf 'Error: provide a direct HTTP(S) APK URL.\n' >&2; exit 1 ;;
esac

if [[ -z "$TV" || "$TV" == -* || "$TV" == *[[:space:]]* ]]; then
  printf 'Error: provide a TV hostname or IP address, optionally with a port.\n' >&2
  exit 1
fi
if [[ "$TV" != *:* ]]; then
  TV="$TV:5555"
fi

for tool in adb curl unzip; do
  if ! command -v "$tool" > /dev/null 2>&1; then
    printf 'Error: %s must be installed and available on PATH.\n' "$tool" >&2
    exit 1
  fi
done

printf 'Connecting to %s...\n' "$TV"
adb connect "$TV"
if ! adb -s "$TV" get-state > /dev/null; then
  printf 'Error: wake the TV, enable ADB debugging, and allow this Mac on the TV.\n' >&2
  exit 1
fi

DOWNLOADS="$(mktemp -d)"
trap 'rm -rf "$DOWNLOADS"' EXIT

printf 'Downloading APK...\n'
curl --fail --location --show-error --progress-bar \
  --connect-timeout 15 --max-time 300 \
  --proto '=http,https' --proto-redir '=http,https' \
  --output "$DOWNLOADS/app.apk" -- "$URL"

if ! unzip -p "$DOWNLOADS/app.apk" AndroidManifest.xml > /dev/null 2>&1; then
  printf 'Error: the download is not an APK. Use the direct APK link, not a web page.\n' >&2
  exit 1
fi

printf 'Installing on %s...\n' "$TV"
adb -s "$TV" install -r "$DOWNLOADS/app.apk"
