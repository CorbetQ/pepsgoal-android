#!/usr/bin/env bash
set -euo pipefail
repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$repo/scripts/dev/env.sh"
cd "$repo"
git status --short
git branch --show-current
git log -1 --oneline
missing=0
for tool in "$JAVA_HOME/bin/javac" "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "$ANDROID_HOME/platform-tools/adb"; do
  if [[ -x "$tool" ]]; then echo "OK $tool"; else echo "ABSENT $tool"; missing=1; fi
done
if [[ -x "$JAVA_HOME/bin/javac" ]]; then
  java_version="$("$JAVA_HOME/bin/javac" -version 2>&1)"
  if [[ "$java_version" == *'javac 17.'* ]]; then echo 'OK JDK 17'; else echo 'JDK 17 requis pour cet environnement.'; missing=1; fi
fi
for item in platforms/android-36/android.jar build-tools/35.0.0/aapt2; do
  if [[ -f "$ANDROID_HOME/$item" ]]; then echo "OK $item"; else echo "ABSENT $item"; missing=1; fi
done
if [[ -r /dev/kvm && -w /dev/kvm ]]; then echo 'KVM disponible'; else echo 'KVM indisponible : utiliser un appareil ADB ou un hôte avec virtualisation pour les captures/perfs.'; fi
if command -v adb >/dev/null; then adb devices -l; fi
exit "$missing"
