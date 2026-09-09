#!/usr/bin/env bash
# Launches an Android emulator with DNS forced to Cloudflare (1.1.1.1, 1.0.0.1).
#
# Android Studio's AVD Manager ignores -dns-server, so to override DNS you have
# to start the emulator from the CLI like this. Re-launch this script any time
# you need a fresh emulator process with the override applied.
#
# Usage:
#   ./scripts/run-emulator.sh                # picks first AVD, or prompts if several
#   ./scripts/run-emulator.sh <AvdName>      # launches that specific AVD
#   ./scripts/run-emulator.sh -l             # lists available AVDs and exits

set -euo pipefail

DNS_SERVERS="1.1.1.1,1.0.0.1"

# Resolve ANDROID_HOME / ANDROID_SDK_ROOT, falling back to the macOS default.
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
EMULATOR_BIN="$SDK_ROOT/emulator/emulator"

if [[ ! -x "$EMULATOR_BIN" ]]; then
  echo "error: emulator binary not found at $EMULATOR_BIN" >&2
  echo "       set ANDROID_HOME (or ANDROID_SDK_ROOT) to your SDK location." >&2
  exit 1
fi

list_avds() {
  "$EMULATOR_BIN" -list-avds
}

if [[ "${1:-}" == "-l" || "${1:-}" == "--list" ]]; then
  list_avds
  exit 0
fi

AVD_NAME="${1:-}"

if [[ -z "$AVD_NAME" ]]; then
  mapfile -t AVDS < <(list_avds)
  if [[ ${#AVDS[@]} -eq 0 ]]; then
    echo "error: no AVDs found. Create one in Android Studio first." >&2
    exit 1
  elif [[ ${#AVDS[@]} -eq 1 ]]; then
    AVD_NAME="${AVDS[0]}"
  else
    echo "Multiple AVDs found. Pick one:"
    select choice in "${AVDS[@]}"; do
      if [[ -n "${choice:-}" ]]; then
        AVD_NAME="$choice"
        break
      fi
    done
  fi
fi

echo "Launching emulator: $AVD_NAME  (DNS=$DNS_SERVERS)"
exec "$EMULATOR_BIN" -avd "$AVD_NAME" -dns-server "$DNS_SERVERS" "${@:2}"
