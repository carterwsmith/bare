#!/usr/bin/env bash
# Configure the pinned Home on Fire UI without requiring remote-control input.
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <adb-serial>" >&2
  exit 1
fi
if ! command -v xmllint > /dev/null 2>&1; then
  echo 'Error: xmllint is required to configure Home on Fire (included with macOS).' >&2
  exit 1
fi

TV="$1"
LAUNCHER=com.csmith.barelauncher
HOME_ON_FIRE=io.github.toolicious.homeonfire
WORK_DIR="$(mktemp -d)"
REMOTE_UI="/data/local/tmp/bare-setup-$$.xml"
trap 'adb -s "$TV" shell rm -f "$REMOTE_UI" > /dev/null 2>&1 || true; rm -rf "$WORK_DIR"' EXIT

read_ui() {
  adb -s "$TV" shell uiautomator dump "$REMOTE_UI" > /dev/null
  adb -s "$TV" exec-out cat "$REMOTE_UI" > "$WORK_DIR/ui.xml"
}

ui_value() {
  xmllint --xpath "string($1)" "$WORK_DIR/ui.xml"
}

tap_node() {
  local bounds
  bounds="$(ui_value "($1)/@bounds")"
  if [[ ! "$bounds" =~ ^\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]$ ]]; then
    echo "Error: could not locate a Home on Fire control: $1" >&2
    exit 1
  fi
  adb -s "$TV" shell input tap \
    "$(( (BASH_REMATCH[1] + BASH_REMATCH[3]) / 2 ))" \
    "$(( (BASH_REMATCH[2] + BASH_REMATCH[4]) / 2 ))"
}

enable_switch() {
  local row state
  row="//node[@text='$1']/parent::node"
  state="$(ui_value "$row/node[@checkable='true']/@checked")"
  if [[ "$state" == false ]]; then
    tap_node "$row"
    read_ui
    state="$(ui_value "$row/node[@checkable='true']/@checked")"
  fi
  if [[ "$state" != true ]]; then
    echo "Error: Home on Fire did not enable '$1'." >&2
    exit 1
  fi
}

adb -s "$TV" shell input keyevent KEYCODE_WAKEUP
adb -s "$TV" shell am start -W -n "$HOME_ON_FIRE/.MainActivity" > /dev/null
read_ui
# v1.5.2's first clickable control in the scroll view is the target-app chip.
tap_node "(//node[@class='android.widget.ScrollView']//node[@clickable='true'])[1]"
read_ui

PREVIOUS_UI=''
while [[ "$(ui_value "//node[@text='$LAUNCHER']/@bounds")" == '' ]]; do
  CURRENT_UI="$(< "$WORK_DIR/ui.xml")"
  if [[ "$CURRENT_UI" == "$PREVIOUS_UI" ]]; then
    echo 'Error: Bare Launcher was not found in the Home on Fire app picker.' >&2
    exit 1
  fi
  PREVIOUS_UI="$CURRENT_UI"
  adb -s "$TV" shell input keyevent KEYCODE_PAGE_DOWN
  read_ui
done
# Each picker row contains a text column with the label and exact package name.
tap_node "//node[@text='$LAUNCHER']/parent::node/parent::node"
read_ui

if [[ "$(ui_value "//node[contains(@text, '($LAUNCHER)')]/@text")" == '' ]]; then
  echo 'Error: Home on Fire did not save Bare Launcher as its target.' >&2
  exit 1
fi
enable_switch 'Accessibility service (required)'
enable_switch 'Replace ⌂ Home button'
enable_switch 'Launch on boot'

adb -s "$TV" shell am start -W -n "$LAUNCHER/.home.MainActivity" > /dev/null
