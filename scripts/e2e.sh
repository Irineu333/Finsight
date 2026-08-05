#!/usr/bin/env bash
#
# Runs the Maestro E2E suite against a connected Android device or emulator.
#
#   scripts/e2e.sh                          # build, install, run every flow
#   scripts/e2e.sh --skip-build             # reuse the APK already installed
#   scripts/e2e.sh --tags smoke             # only the flows carrying a tag
#   scripts/e2e.sh .maestro/flows/smoke     # only a folder, or a single .yaml
#
set -euo pipefail

readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly APK="$ROOT/app/android/build/outputs/apk/debug/android-debug.apk"

skip_build=false
tags=""
target=".maestro"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-build) skip_build=true; shift ;;
        --tags) tags="$2"; shift 2 ;;
        -h|--help) sed -n '2,10p' "${BASH_SOURCE[0]}"; exit 0 ;;
        *) target="$1"; shift ;;
    esac
done

cd "$ROOT"

command -v maestro >/dev/null || {
    echo "maestro not found. Install it with: curl -Ls https://get.maestro.mobile.dev | bash" >&2
    exit 1
}

[[ -n "$(adb devices | sed '1d' | grep -w device || true)" ]] || {
    echo "No Android device attached. Start an emulator first." >&2
    exit 1
}

# While another Maestro session holds the device — Maestro Studio, most often — the CLI skips
# setting up its driver and then fails on the first command with a bare gRPC error that names
# none of this (mobile-dev-inc/maestro#3065). Say it here, where it is still legible.
if [[ -s "$HOME/.maestro/sessions" ]]; then
    echo "Warning: another Maestro session is registered in ~/.maestro/sessions." >&2
    echo "  Close Maestro Studio. If nothing is running, the entry is stale: rm ~/.maestro/sessions" >&2
fi

# The flows assert English labels and English-formatted amounts, so the device's language is part
# of the contract, not an accident of whoever's machine is running them. Refuse rather than let a
# Portuguese device turn every assertion red for a reason no failure message would name.
#
# It is checked, never set: the app reads `persist.sys.locale`, and writing that needs root plus a
# framework restart — too fragile to hide inside a test run. `pm clear` also wipes any per-app
# locale, and every flow starts by clearing state, so that route cannot hold either.
device_locale="$(adb shell getprop persist.sys.locale | tr -d '\r')"
[[ -n "$device_locale" ]] || device_locale="$(adb shell getprop ro.product.locale | tr -d '\r')"

if [[ "$device_locale" != en* ]]; then
    echo "Device language is '${device_locale:-unknown}'; the flows require English." >&2
    echo "  Change it in Settings > System > Languages, or on a userdebug emulator:" >&2
    echo "    adb root && adb shell setprop persist.sys.locale en-US && adb shell 'stop; start'" >&2
    exit 1
fi

if [[ "$skip_build" == false ]]; then
    ./gradlew :app:android:assembleDebug
    # -t allows the debug (test-only) build; -r keeps the flows free to clear state themselves.
    adb install -r -t "$APK"
fi

# The workspace config lives in .maestro/config.yaml and is picked up from the folder argument.
if [[ -n "$tags" ]]; then
    exec maestro test --include-tags "$tags" "$target"
else
    exec maestro test "$target"
fi
