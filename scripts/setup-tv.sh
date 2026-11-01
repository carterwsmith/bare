#!/usr/bin/env bash
# Builds Bare Launcher and installs it on a Fire TV over ADB, together with Home on Fire,
# which sends the remote's Home button to it.
#
# Usage: scripts/setup-tv.sh <fire-tv-ip>
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <fire-tv-ip>" >&2
  exit 1
fi

TV="$1:5555"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LAUNCHER=com.csmith.barelauncher
HOME_ON_FIRE=io.github.toolicious.homeonfire
# Pinned to the release that was security-reviewed. Update both lines together, and only
# after reviewing the new release.
HOME_ON_FIRE_APK=https://github.com/toolicious/home-on-fire/releases/download/v1.5.2/home-on-fire.apk
HOME_ON_FIRE_SHA256=87403c961264cc2e36e53cf571777ec20705eb87f46794cb0ac667135a98d9fe

export JAVA_HOME="${JAVA_HOME:-$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home}"
"$ROOT/gradlew" -p "$ROOT" --quiet assembleRelease

adb connect "$TV"
echo "If the TV asks whether to allow debugging, choose Allow."
adb -s "$TV" wait-for-device

adb -s "$TV" install -r "$ROOT/app/build/outputs/apk/release/app-release.apk"
# Compiles the whole launcher to native code now, instead of Fire OS interpreting it at first.
adb -s "$TV" shell cmd package compile -m speed -f "$LAUNCHER"
# Lets the Install APK screen work without a trip to Developer Options.
adb -s "$TV" shell appops set "$LAUNCHER" REQUEST_INSTALL_PACKAGES allow
# Lets the Keep watching row see what other apps are playing.
adb -s "$TV" shell cmd notification allow_listener "$LAUNCHER/.watching.NowPlayingListener"

DOWNLOADS="$(mktemp -d)"
trap 'rm -rf "$DOWNLOADS"' EXIT
curl -fsSL -o "$DOWNLOADS/home-on-fire.apk" "$HOME_ON_FIRE_APK"
echo "$HOME_ON_FIRE_SHA256  $DOWNLOADS/home-on-fire.apk" | shasum -a 256 -c -
adb -s "$TV" install -r "$DOWNLOADS/home-on-fire.apk"
# Lets Home on Fire switch on its own accessibility service; some Fire OS builds hide that toggle.
adb -s "$TV" shell pm grant "$HOME_ON_FIRE" android.permission.WRITE_SECURE_SETTINGS

adb -s "$TV" shell am start -n "$HOME_ON_FIRE/.MainActivity" > /dev/null
echo "Done. Home on Fire is open on the TV: turn it on, pick Bare Launcher as the target,"
echo "and turn on Launch on boot so the TV starts on Bare Launcher after a reboot or standby."
