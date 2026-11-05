#!/usr/bin/env bash
# Read-only snapshots on a dedicated test device. No simulation or constant ADB polling.
set -euo pipefail
serial="${1:?Usage: collect.sh SERIAL RUN_DIRECTORY before|after [PACKAGE]}"
out="${2:?RUN_DIRECTORY required}"
phase="${3:?before|after required}"
package="${4:-fr.pepsgoal.app}"
case "$phase" in before|after) ;; *) exit 2 ;; esac
[[ "$package" =~ ^[a-zA-Z0-9_.]+$ ]] || exit 2
adb_bin="${ADB:-${ANDROID_HOME:+$ANDROID_HOME/platform-tools/}adb}"
repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
mkdir -p "$out"
mkdir "$out/$phase" # refuse to overwrite an existing observation
out="$(cd "$out/$phase" && pwd)"
"$adb_bin" -s "$serial" get-state >/dev/null
{
  date -u +%FT%TZ
  git -C "$repo" rev-parse HEAD
  if ! git -C "$repo" diff --quiet; then echo 'working_tree=dirty'; fi
  "$adb_bin" -s "$serial" shell getprop ro.product.model
  "$adb_bin" -s "$serial" shell getprop ro.build.fingerprint
  "$adb_bin" -s "$serial" shell getprop ro.kernel.qemu
  "$adb_bin" -s "$serial" shell cat /proc/uptime
} > "$out/identity.txt"
for service in battery batterystats alarm jobscheduler thermalservice power; do
  "$adb_bin" -s "$serial" shell dumpsys "$service" > "$out/$service.txt" 2> "$out/$service.stderr.txt" || true
done
"$adb_bin" -s "$serial" shell dumpsys package "$package" > "$out/package.txt"
"$adb_bin" -s "$serial" shell dumpsys batterystats --checkin > "$out/batterystats-checkin.csv"
echo "$out"
echo 'Diagnostics only. A physical discharge, paired control and repeated scenarios are required for pp/h.'
