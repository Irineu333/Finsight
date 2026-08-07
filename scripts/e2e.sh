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
readonly E2E_APP_ID="com.neoutils.finsight"

# The suite is calibrated against one device, and the flows are only as reproducible as the screen
# they scroll on. A different density or height changes what sits below the fold, which is the
# difference between `scrollUntilVisible` finding a field and the run turning red. The CI workflow
# pins the same API level and profile.
readonly E2E_AVD="${E2E_AVD:-finsight_e2e}"
readonly E2E_API=36
readonly E2E_PROFILE=pixel_6
readonly E2E_SCREEN="1080x2400"
readonly E2E_DENSITY=420
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
        # Created here rather than left as a step in a README, because a device assembled by hand
        # is a device assembled differently each time — and every line of it is an input to the
        # test. `avdmanager` answers "no" to the custom-hardware prompt on its own only when it is
        # not asked; it is asked whenever stdin is a terminal.
        echo "Creating $E2E_AVD ($E2E_PROFILE, API $E2E_API)..."
        "$ANDROID_SDK/cmdline-tools/latest/bin/avdmanager" create avd \
            -n "$E2E_AVD" -d "$E2E_PROFILE" -k "$E2E_IMAGE" <<< "no" || {
            echo "Could not create '$E2E_AVD'. Install the image once with:" >&2
            echo "  \$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \"$E2E_IMAGE\"" >&2
            exit 1
        }
    fi
    # `avdmanager` has no flag for any of this — it takes a device profile and nothing else — so the
    # keyboard is written into config.ini afterwards, whether the AVD was just created or has been
    # sitting there since last month. Both keys are read at startup and neither can be repaired from
    # `adb` once the emulator is up. CI applies the same script to the AVD its emulator action
    # creates (see .github/workflows/e2e-android.yml).
    "$ROOT/scripts/pin_avd_keyboard.sh" "$E2E_AVD" >/dev/null

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

# Asking for the ordinary on-screen keyboard rather than the toolbar a hardware keyboard invites.
# It only has an effect where a hardware keyboard exists — which the check below refuses outright —
# so it is a nudge, not the guarantee.
adb shell settings put secure show_ime_with_hard_keyboard 0 >/dev/null 2>&1 || true

# The device's own `Configuration` — the same one the app resolves its resources against, rather
# than properties that may or may not reach it. On the pinned device it reads:
#
#   ...-en-rUS-...-420dpi-finger-keysexposed-nokeys-navhidden-nonav-2400x1080-v36
#
# Read `nokeys` from here and nothing else: the density in this string is the *bucket* name whenever
# there is one (480 prints as `xxhdpi`), so matching a number against it works for 420 and quietly
# stops working for whoever repins the profile.
device_config="$(adb shell am get-config 2>/dev/null | tr -d '\r')"

# The screen is part of the contract, not of whoever's machine is running the suite: the flows
# scroll, and density and height are what decide whether `scrollUntilVisible` reaches a field or the
# run turns red. A tablet in English on API 36 would pass every other check and still be an invalid
# result.
device_size="$(adb shell wm size | sed -n 's/^Physical size: *//p' | tr -d '\r')"
device_density="$(adb shell wm density | sed -n 's/^Physical density: *//p' | tr -d '\r')"
if [[ "$device_size" != "$E2E_SCREEN" || "$device_density" != "$E2E_DENSITY" ]]; then
    echo "Attached device is ${device_size:-?} @ ${device_density:-?}dpi;" \
         "the flows are pinned to the $E2E_PROFILE screen ($E2E_SCREEN @ ${E2E_DENSITY}dpi)." >&2
    echo "  Close it and re-run to boot '$E2E_AVD', or set E2E_AVD to a device you trust." >&2
    exit 1
fi

# `nokeys` is Android's own word for "no hardware keyboard", and it is the half of the keyboard
# contract that no `adb` command can repair: it comes from `hw.keyboard` in the AVD's config.ini,
# which is read at boot. With a hardware keyboard present, Gboard shows a floating toolbar over the
# open sheet instead of a keyboard and the text typed underneath is lost — silently, and three
# screens later.
if [[ "$device_config" != *-nokeys-* ]]; then
    echo "Attached device advertises a hardware keyboard; the flows type on the on-screen one." >&2
    echo "  Shut the emulator down, then: scripts/pin_avd_keyboard.sh $E2E_AVD" >&2
    exit 1
fi

# And the other half: a soft keyboard the device will actually put up. A system image with no IME
# installed fails every flow at its first `inputText`, for a reason no failure message would name.
device_ime="$(adb shell settings get secure default_input_method | tr -d '\r')"
if [[ -z "$device_ime" || "$device_ime" == "null" ]]; then
    echo "Attached device has no default input method; the flows need an on-screen keyboard." >&2
    echo "  Use a system image that ships one (the pinned $E2E_IMAGE does)." >&2
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

# `--skip-build` means "reuse what is installed", and on a device that has nothing installed — a
# freshly created AVD, most often — it would otherwise hand every flow the same unhelpful
# "Package com.neoutils.finsight is not installed". Install what is on disk when there is something
# on disk; build when there is not.
if [[ "$skip_build" == true ]] &&
   ! adb shell pm list packages 2>/dev/null | grep -q "^package:$E2E_APP_ID\$"; then
    echo "$E2E_APP_ID is not installed on the device; ignoring --skip-build."
    skip_build=false
    [[ -f "$APK" ]] && { adb install -r -t "$APK" && skip_build=true; }
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
