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

# The suite is calibrated against one device, and the flows are only as reproducible as the screen
# they scroll on. A different density or height changes what sits below the fold, which is the
# difference between `scrollUntilVisible` finding a field and the run turning red. The CI workflow
# pins the same API level and profile.
readonly E2E_AVD="${E2E_AVD:-finsight_e2e}"
readonly E2E_API=36
readonly E2E_PROFILE=pixel_6
readonly E2E_IMAGE="system-images;android-36;google_apis_playstore;arm64-v8a"
readonly ANDROID_SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

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

# Nothing attached: boot the pinned emulator rather than let the run pick up whatever happens to
# be plugged in.
if [[ -z "$(adb devices | sed '1d' | grep -w device || true)" ]]; then
    if ! "$ANDROID_SDK/emulator/emulator" -list-avds | grep -qx "$E2E_AVD"; then
        echo "The pinned emulator '$E2E_AVD' does not exist. Create it once with:" >&2
        echo "  \$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd \\" >&2
        echo "      -n $E2E_AVD -d $E2E_PROFILE -k \"$E2E_IMAGE\"" >&2
        exit 1
    fi
    echo "Booting $E2E_AVD..."
    "$ANDROID_SDK/emulator/emulator" -avd "$E2E_AVD" -no-snapshot-load -no-boot-anim >/dev/null 2>&1 &
    until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
        sleep 3
    done
fi

# Something is attached: it still has to be the device the flows were written against.
device_api="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ "$device_api" != "$E2E_API" ]]; then
    echo "Attached device runs API ${device_api:-unknown}; the flows are pinned to API $E2E_API." >&2
    echo "  Close it and re-run to boot '$E2E_AVD', or set E2E_AVD to a device you trust." >&2
    exit 1
fi

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
