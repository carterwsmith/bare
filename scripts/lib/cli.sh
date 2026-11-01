#!/usr/bin/env bash
# Callers provide CLI_LOG inside their temporary directory.

cli_step() {
  local label status
  label="$1"
  shift
  printf '%s... ' "$label"
  if "$@" > "$CLI_LOG" 2>&1; then
    printf 'done\n'
  else
    status=$?
    printf 'failed\n' >&2
    cat "$CLI_LOG" >&2
    return "$status"
  fi
}

cli_connect() {
  local tv output
  tv="$1"
  printf 'Connecting to %s... ' "$tv"
  if ! output="$(adb connect "$tv" 2>&1)"; then
    printf 'failed\n%s\n' "$output" >&2
    return 1
  fi
  if output="$(adb -s "$tv" get-state 2>&1)"; then
    printf 'done\n'
  elif [[ "$output" == *unauthorized* ]]; then
    printf 'approval needed\n'
    cli_step 'Allow debugging on the TV' adb -s "$tv" wait-for-device
  else
    printf 'failed\nError: wake the TV and enable ADB debugging.\n%s\n' "$output" >&2
    return 1
  fi
}
