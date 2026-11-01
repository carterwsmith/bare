#!/usr/bin/env bash
# Download an APK, report progress on Bare Launcher, and install it over ADB.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/cli.sh"

usage() {
  printf 'Usage: %s <apk-url> <tv-address>\n' "$0"
}

if [[ "${1:-}" == --help || "${1:-}" == -h ]]; then
  usage
  printf '\nDownload an APK and install it on the TV, keeping existing app data.\n'
  exit 0
fi

if [[ $# -ne 2 ]]; then
  if [[ $# -eq 1 ]]; then
    printf 'Error: missing TV address.\n' >&2
  else
    printf 'Error: provide an APK URL and TV address.\n' >&2
  fi
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

for tool in adb curl unzip base64; do
  if ! command -v "$tool" > /dev/null 2>&1; then
    printf 'Error: %s must be installed and available on PATH.\n' "$tool" >&2
    exit 1
  fi
done

DOWNLOADS="$(mktemp -d)"
CLI_LOG="$DOWNLOADS/command.log"
INSTALL_ID="cli-${DOWNLOADS##*/}"
# Only base64 crosses the remote shell, so URL query strings cannot become shell commands.
URL_BASE64="$(printf '%s' "$URL" | base64 | tr -d '\n')"
PROCESS_PID=''
REMOTE_APK=''
INSTALL_STARTED=false
INSTALL_FINISHED=false
LAST_PERCENT=''

report_install() {
  local phase result path
  phase="$1"
  shift
  result="$(adb -s "$TV" shell am broadcast --receiver-foreground \
    -n com.csmith.barelauncher/.install.InstallProgressReceiver \
    --es install_id "$INSTALL_ID" --es url_base64 "$URL_BASE64" --es phase "$phase" "$@")"
  if [[ "$result" == *'Broadcast completed: result=1'* ]]; then
    INSTALL_FINISHED=true
    printf '\nError: the TV could not read this APK.\n' >&2
    return 1
  fi
  if [[ "$result" != *'Broadcast completed: result=-1'* ]]; then
    printf '\nError: Bare Launcher could not accept install progress. Update it with scripts/setup-tv.sh first.\n%s\n' "$result" >&2
    return 1
  fi
  if [[ -z "$REMOTE_APK" ]]; then
    path="${result##*data=\"}"
    path="${path%%\"*}"
    if [[ ! "$path" =~ ^/[A-Za-z0-9_./-]+$ ]] ||
       [[ "$path" != */Android/data/com.csmith.barelauncher/files/installs/"$INSTALL_ID.apk" ]]; then
      printf '\nError: update Bare Launcher with scripts/setup-tv.sh before using this script.\n' >&2
      return 1
    fi
    REMOTE_APK="$path"
  fi
}

finish_install() {
  INSTALL_FINISHED=true
  report_install "$@"
}

cleanup() {
  local status=$?
  trap - EXIT
  if [[ -n "$PROCESS_PID" ]]; then
    kill "$PROCESS_PID" > /dev/null 2>&1 || true
    wait "$PROCESS_PID" > /dev/null 2>&1 || true
  fi
  if [[ "$INSTALL_STARTED" == true && "$INSTALL_FINISHED" != true ]]; then
    report_install Failed --es error Cancelled > /dev/null 2>&1 || true
  fi
  if [[ -n "$REMOTE_APK" ]]; then
    adb -s "$TV" shell rm -f "$REMOTE_APK" > /dev/null 2>&1 || true
  fi
  rm -rf "$DOWNLOADS"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

report_bytes() {
  local phase total received percent label
  phase="$1"
  total="$2"
  received="$3"
  case "$phase" in
    Downloading) label='Downloading APK' ;;
    Transferring) label='Sending to TV' ;;
  esac
  if (( total > 0 )); then
    percent=$(( received * 100 / total ))
    if (( percent > 100 )); then percent=100; fi
    if [[ "$percent" != "$LAST_PERCENT" ]]; then
      report_install "$phase" --ei percent "$percent"
      printf '\r%s... %3d%%' "$label" "$percent"
      LAST_PERCENT="$percent"
    fi
  fi
}

cli_connect "$TV"
printf 'Downloading APK... '
report_install Downloading
INSTALL_STARTED=true
: > "$DOWNLOADS/app.apk"
: > "$DOWNLOADS/headers"
curl --fail --location --silent --show-error \
  --connect-timeout 15 --max-time 300 \
  --proto '=http,https' --proto-redir '=http,https' \
  --dump-header "$DOWNLOADS/headers" --output "$DOWNLOADS/app.apk" -- "$URL" \
  2> "$DOWNLOADS/download.log" &
PROCESS_PID=$!
while kill -0 "$PROCESS_PID" > /dev/null 2>&1; do
  # Read the final response's size from this transfer, not an extra HEAD request.
  TOTAL_BYTES="$(awk '/^HTTP\// { total=0 } tolower($1)=="content-length:" { total=$2 } END { printf "%.0f", total }' "$DOWNLOADS/headers")"
  RECEIVED_BYTES="$(wc -c < "$DOWNLOADS/app.apk")"
  report_bytes Downloading "$TOTAL_BYTES" "$RECEIVED_BYTES"
  sleep 0.25
done
if wait "$PROCESS_PID"; then
  PROCESS_PID=''
else
  DOWNLOAD_STATUS=$?
  PROCESS_PID=''
  printf '\n' >&2
  tail -n 3 "$DOWNLOADS/download.log" >&2
  finish_install Failed --es error DownloadFailed
  exit "$DOWNLOAD_STATUS"
fi
report_install Downloading --ei percent 100
printf '\rDownloading APK... 100%%\n'
check_apk() { unzip -p "$DOWNLOADS/app.apk" AndroidManifest.xml > /dev/null; }
if ! cli_step 'Checking APK' check_apk; then
  finish_install Failed --es error InvalidApk
  printf 'Error: the download is not an APK. Use the direct APK link, not a web page.\n' >&2
  exit 1
fi

# Separate the network transfer from PackageInstaller's verification/install work.
APK_BYTES="$(wc -c < "$DOWNLOADS/app.apk")"
printf 'Sending to TV... '
report_install Transferring --ei percent 0
adb -s "$TV" push "$DOWNLOADS/app.apk" "$REMOTE_APK" > "$DOWNLOADS/transfer.log" 2>&1 &
PROCESS_PID=$!
LAST_PERCENT=''
while kill -0 "$PROCESS_PID" > /dev/null 2>&1; do
  # The sync service creates the destination asynchronously when the transfer starts.
  RECEIVED_BYTES="$(adb -s "$TV" shell "if [ -f $REMOTE_APK ]; then stat -c %s $REMOTE_APK; else echo 0; fi" | tr -d '\r')"
  report_bytes Transferring "$APK_BYTES" "$RECEIVED_BYTES"
  sleep 0.25
done
if wait "$PROCESS_PID"; then
  PROCESS_PID=''
else
  PROCESS_PID=''
  printf '\n' >&2
  tail -n 3 "$DOWNLOADS/transfer.log" >&2
  finish_install Failed --es error TransferFailed
  exit 1
fi
report_install Transferring --ei percent 100
printf '\rSending to TV... 100%%\n'
cli_step 'Verifying APK' report_install Verifying

install_apk() {
  report_install Installing || return
  # Shell reads the app-owned staging file; PackageInstaller reads the pipe.
  adb -s "$TV" shell "cat $REMOTE_APK | pm install -r -S $APK_BYTES" || return
  finish_install Installed
}
if ! cli_step 'Installing APK' install_apk; then
  finish_install Failed --es error InstallFailed
  exit 1
fi
printf 'Installed.\n'
