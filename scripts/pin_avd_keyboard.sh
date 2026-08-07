#!/usr/bin/env bash
#
# Gives an AVD a phone's keyboard: the on-screen one, and no hardware keyboard behind it.
#
#   scripts/pin_avd_keyboard.sh finsight_e2e
#
# `avdmanager` leaves the AVD advertising a hardware keyboard, and that is what tips Android into
# treating the host's keyboard as the device's: Gboard puts up a small floating toolbar instead of a
# keyboard, it overlays whatever sheet is open, and text typed into a field underneath it is simply
# lost — with no error, and three screens later, as a button that does not submit.
#
# Both keys matter and both are read at boot, so this has to run before the emulator starts and
# cannot be repaired from `adb` afterwards. That is also why it lives here rather than inside
# `scripts/e2e.sh`: the local run boots its own AVD, CI has one booted for it by the emulator
# action, and the rule is the same one in both places.
set -euo pipefail

readonly AVD="${1:?usage: pin_avd_keyboard.sh <avd-name>}"
readonly CONFIG="${ANDROID_AVD_HOME:-$HOME/.android/avd}/$AVD.avd/config.ini"

[[ -f "$CONFIG" ]] || {
    echo "No config.ini for AVD '$AVD' at $CONFIG" >&2
    exit 1
}

tmp="$(mktemp)"
grep -v -E '^hw\.keyboard(\.lid)? *=' "$CONFIG" > "$tmp"
printf 'hw.keyboard = no\nhw.keyboard.lid = no\n' >> "$tmp"
mv "$tmp" "$CONFIG"

echo "AVD '$AVD': hw.keyboard = no, hw.keyboard.lid = no"
