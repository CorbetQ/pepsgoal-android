#!/usr/bin/env bash
# Captures de la fiche Play Store pour une langue (fr, en) et un thème (light, dark) : installe l'APK debug
# et ouvre chaque écran avec les données de démonstration (version 4.0.1). Un émulateur neuf par appel (plus stable sur la CI).
set -u
LANG_=${1:-fr}; THEME=${2:-light}
PKG=fr.pepsgoal.app.debug
timeout 120 adb wait-for-device || true
for w in $(seq 1 60); do [ "$(timeout 10 adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && break; sleep 2; done
for try in 1 2 3; do adb install -r -g app/build/outputs/apk/github/debug/app-github-debug.apk && break; sleep 10; done
adb shell svc power stayon true
adb shell cmd uimode night $([ "$THEME" = dark ] && echo yes || echo no)
# Barre d'état propre (mode démo système)
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930 >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null
out=shots/$LANG_/$THEME; mkdir -p "$out"
timeout 90 adb shell am start -W -n $PKG/fr.pepsgoal.app.MainActivity >/dev/null
sleep 15
n=1
for s in today goals goal stats week journal profile focus; do
  # Jusqu'à 3 essais : l'émulateur de la CI se fige parfois quelques secondes
  for try in 1 2 3; do
    timeout 60 adb wait-for-device || true
    timeout 60 adb shell am start -W -n $PKG/fr.pepsgoal.app.MainActivity --es shot $s --es lang $LANG_ >/dev/null
    sleep $((4 + try * 2))
    timeout 30 adb exec-out screencap -p > "$out/$n-$s.png"
    size=$(stat -c %s "$out/$n-$s.png")
    echo "$out/$n-$s.png : $size octets (essai $try)"
    [ "$size" -gt 50000 ] && break
  done
  n=$((n+1))
done
timeout 20 adb shell am force-stop $PKG
exit 0
