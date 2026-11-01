#!/usr/bin/env bash
# Builds Bare Launcher and installs it on a Fire TV over ADB, together with Home on Fire,
# which sends the remote's Home button to it.
#
# Usage: scripts/setup-tv.sh <fire-tv-ip>
set -euo pipefail

if [[ "${1:-}" == --help || "${1:-}" == -h ]]; then
  echo "Usage: $0 <fire-tv-ip>" >&2
  exit 0
fi
if [[ $# -ne 1 ]]; then
  echo 'Error: provide the TV IP address.' >&2
  echo "Usage: $0 <fire-tv-ip>" >&2
  exit 1
fi

TV="$1:5555"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/scripts/lib/cli.sh"
LAUNCHER=com.csmith.barelauncher
HOME_ON_FIRE=io.github.toolicious.homeonfire
# Pinned to the release that was security-reviewed. Update both lines together, and only
# after reviewing the new release.
HOME_ON_FIRE_APK=https://github.com/toolicious/home-on-fire/releases/download/v1.5.2/home-on-fire.apk
HOME_ON_FIRE_SHA256=87403c961264cc2e36e53cf571777ec20705eb87f46794cb0ac667135a98d9fe

DOWNLOADS="$(mktemp -d)"
CLI_LOG="$DOWNLOADS/command.log"
trap 'rm -rf "$DOWNLOADS"' EXIT

build_launcher() {
  local java_prefix
  if [[ -z "${JAVA_HOME:-}" ]]; then
    java_prefix="$(brew --prefix openjdk@21)" || return
    export JAVA_HOME="$java_prefix/libexec/openjdk.jdk/Contents/Home"
  fi
  "$ROOT/gradlew" -p "$ROOT" --quiet assembleRelease
}

prepare_launcher() {
  adb -s "$TV" shell cmd package compile -m speed -f "$LAUNCHER" || return
  adb -s "$TV" shell appops set "$LAUNCHER" REQUEST_INSTALL_PACKAGES allow || return
  adb -s "$TV" shell cmd notification allow_listener "$LAUNCHER/.watching.NowPlayingListener"
}

download_helper() {
  curl -fsSL -o "$DOWNLOADS/home-on-fire.apk" "$HOME_ON_FIRE_APK" || return
  printf '%s  %s\n' "$HOME_ON_FIRE_SHA256" "$DOWNLOADS/home-on-fire.apk" | shasum -a 256 -c -
}

install_helper() {
  adb -s "$TV" install -r "$DOWNLOADS/home-on-fire.apk" || return
  adb -s "$TV" shell pm grant "$HOME_ON_FIRE" android.permission.WRITE_SECURE_SETTINGS
}

cli_step 'Building launcher' build_launcher
cli_connect "$TV"
cli_step 'Installing launcher' adb -s "$TV" install -r "$ROOT/app/build/outputs/apk/release/app-release.apk"
cli_step 'Preparing launcher' prepare_launcher
cli_step 'Downloading Home on Fire' download_helper
cli_step 'Installing Home on Fire' install_helper
cli_step 'Configuring Home and startup' "$ROOT/scripts/configure-home-on-fire.sh" "$TV"
printf 'Ready. Bare Launcher is open.\n'
