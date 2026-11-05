#!/usr/bin/env bash
# Réservé à un émulateur de test jetable : le mode shot charge les données de démo.
set -euo pipefail
repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$repo/scripts/dev/env.sh"
serial="${1:?Usage: capture.sh emulator-SERIAL [today|goals|goal|stats|week|journal|profile|focus] [light|dark] [1.0|2.0]}"
screen="${2:-today}"; theme="${3:-light}"; scale="${4:-1.0}"
[[ "$serial" == emulator-* ]] || { echo 'Utiliser un émulateur jetable, sans compte personnel.' >&2; exit 2; }
case "$screen" in today|goals|goal|stats|week|journal|profile|focus) ;; *) exit 2 ;; esac
case "$theme" in light) night=no ;; dark) night=yes ;; *) exit 2 ;; esac
case "$scale" in 1.0|2.0) ;; *) exit 2 ;; esac
apk="$repo/app/build/outputs/apk/github/debug/app-github-debug.apk"
[[ -f "$apk" ]] || { echo 'Compiler avec scripts/dev/check.sh apk.' >&2; exit 1; }
adb -s "$serial" get-state >/dev/null
[[ "$(adb -s "$serial" shell getprop sys.boot_completed | tr -d '\r')" == 1 ]] || { echo 'Émulateur non démarré.' >&2; exit 1; }
old_scale="$(adb -s "$serial" shell settings get system font_scale | tr -d '\r')"
old_night="$(adb -s "$serial" shell cmd uimode night | tr -d '\r' | awk '{print $NF}')"
case "$old_night" in yes|no|auto|custom_schedule|custom_bedtime) ;; *) echo 'Impossible de préserver le mode nuit.' >&2; exit 1 ;; esac
restore() {
  if [[ "$old_scale" == null ]]; then adb -s "$serial" shell settings delete system font_scale >/dev/null || true
  else adb -s "$serial" shell settings put system font_scale "$old_scale" >/dev/null || true; fi
  adb -s "$serial" shell cmd uimode night "$old_night" >/dev/null || true
}
trap restore EXIT
adb -s "$serial" install -r "$apk" >/dev/null
adb -s "$serial" shell cmd uimode night "$night" >/dev/null
adb -s "$serial" shell settings put system font_scale "$scale" >/dev/null
adb -s "$serial" shell am start -W -n fr.pepsgoal.app.debug/fr.pepsgoal.app.MainActivity --es shot "$screen" --es lang fr >/dev/null
sleep 8
out="$repo/captures/$(date -u +%Y%m%dT%H%M%S)-${screen}-${theme}-${scale}.png"
mkdir -p "$repo/captures"
adb -s "$serial" exec-out screencap -p > "$out"
[[ -s "$out" ]] || { echo 'Capture vide.' >&2; exit 1; }
echo "$out"
