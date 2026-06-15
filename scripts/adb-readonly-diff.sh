#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${1:-"$ROOT_DIR/captures/adb-$(date +%Y%m%d-%H%M%S)"}"
REPORT="$OUT_DIR/report.txt"
ANDROID_AUTO_PACKAGE="com.google.android.projection.gearhead"
WIRELESS_RECEIVER="com.google.android.apps.auto.wireless.bluetooth.WifiBluetoothReceiver"

mkdir -p "$OUT_DIR"/before "$OUT_DIR"/after

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found in PATH" >&2
  exit 1
fi

run_adb() {
  local dest="$1"
  shift
  {
    echo "$ $*"
    "$@" 2>&1 || true
  } > "$dest"
}

capture() {
  local phase="$1"
  local dir="$OUT_DIR/$phase"

  run_adb "$dir/device.txt" adb shell getprop ro.product.manufacturer
  adb shell getprop > "$dir/getprop.txt" 2>&1 || true

  adb shell settings list global > "$dir/settings-global.txt" 2>&1 || true
  adb shell settings list secure > "$dir/settings-secure.txt" 2>&1 || true
  adb shell settings list system > "$dir/settings-system.txt" 2>&1 || true

  adb shell device_config list > "$dir/device-config-list.txt" 2>&1 || true
  adb shell dumpsys device_config > "$dir/dumpsys-device-config.txt" 2>&1 || true

  adb shell dumpsys package com.google.android.projection.gearhead > "$dir/package-android-auto.txt" 2>&1 || true
  adb shell dumpsys package com.google.android.gms > "$dir/package-gms.txt" 2>&1 || true

  adb shell cmd appops get com.google.android.projection.gearhead > "$dir/appops-android-auto.txt" 2>&1 || true
  adb shell cmd appops get com.google.android.gms > "$dir/appops-gms.txt" 2>&1 || true

  adb shell dumpsys activity services com.google.android.projection.gearhead > "$dir/services-android-auto.txt" 2>&1 || true
  adb shell dumpsys activity services com.google.android.gms > "$dir/services-gms.txt" 2>&1 || true
}

component_state() {
  local file="$1"
  awk -v component="$WIRELESS_RECEIVER" '
    /User 0:/ { in_user = 1; section = ""; next }
    in_user && /User [0-9]+:/ { in_user = 0 }
    in_user && /disabledComponents:/ { section = "disabled"; next }
    in_user && /enabledComponents:/ { section = "enabled"; next }
    in_user && index($0, component) { print section; found = 1; exit }
    END { if (!found) print "not_listed" }
  ' "$file"
}

echo "Waiting for an Android device..."
adb wait-for-device

{
  echo "AA Wireless Switch read-only ADB diff"
  echo "time=$(date -Is)"
  echo "output=$OUT_DIR"
  echo
  echo "[adb]"
  adb version 2>&1 || true
  echo
  echo "[device]"
  adb shell getprop ro.product.manufacturer 2>&1 || true
  adb shell getprop ro.product.model 2>&1 || true
  adb shell getprop ro.build.version.release 2>&1 || true
  adb shell getprop ro.build.version.sdk 2>&1 || true
  echo
} > "$REPORT"

echo
echo "1. Set Android Auto Wireless to the first state manually."
read -r -p "Press Enter to capture BEFORE..."
capture before

echo
echo "2. Toggle Android Auto Wireless manually."
read -r -p "Press Enter to capture AFTER..."
capture after

{
  echo
  echo "[android_auto_wireless_receiver]"
  echo "component=$ANDROID_AUTO_PACKAGE/$WIRELESS_RECEIVER"
  echo "before=$(component_state "$OUT_DIR/before/package-android-auto.txt")"
  echo "after=$(component_state "$OUT_DIR/after/package-android-auto.txt")"

  echo
  echo "[changed files]"
  diff -qr "$OUT_DIR/before" "$OUT_DIR/after" || true

  echo
  echo "[settings/device_config diff]"
  diff -u \
    <(cat "$OUT_DIR/before"/settings-*.txt "$OUT_DIR/before/device-config-list.txt" 2>/dev/null | sort) \
    <(cat "$OUT_DIR/after"/settings-*.txt "$OUT_DIR/after/device-config-list.txt" 2>/dev/null | sort) \
    || true

  echo
  echo "[android auto related changes]"
  diff -ru "$OUT_DIR/before" "$OUT_DIR/after" \
    | grep -Ei 'android_auto|android auto|gearhead|projection|wireless|wifi|car|auto' \
    || true
} >> "$REPORT"

echo
echo "Report written to:"
echo "$REPORT"
echo
echo "Send this file back for analysis."
