#!/usr/bin/env bash
# Captures de la fiche Play Store : installe l'APK debug et ouvre chaque écran avec les données de démonstration
set -u
PKG=fr.pepsgoal.app.debug
adb install -r -g app/build/outputs/apk/github/debug/app-github-debug.apk
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell cmd statusbar collapse || true
# Barre d'état propre (mode démo système)
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
mkdir -p shots
adb shell am start -W -n $PKG/fr.pepsgoal.app.MainActivity >/dev/null
sleep 8
for lang in fr en; do
  for mode in no yes; do
    adb shell cmd uimode night $mode
    sleep 2
    n=1
    for s in today goals goal stats week journal profile focus; do
      adb shell am start -W -n $PKG/fr.pepsgoal.app.MainActivity --es shot $s --es lang $lang >/dev/null
      sleep 4
      theme=$([ $mode = yes ] && echo dark || echo light)
      mkdir -p shots/$lang/$theme
      adb exec-out screencap -p > shots/$lang/$theme/$n-$s.png
      echo "shots/$lang/$theme/$n-$s.png"
      n=$((n+1))
    done
  done
done
adb shell am broadcast -a com.android.systemui.demo -e command exit
