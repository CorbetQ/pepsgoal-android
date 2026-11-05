#!/usr/bin/env bash
# Linux x86_64, sans root. Outils/cache hors dépôt ; téléchargements vérifiés.
set -euo pipefail
repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
case "${1:-}" in ''|--accept-sdk-licenses) ;; *) echo 'Usage: bootstrap.sh [--accept-sdk-licenses]'; exit 2 ;; esac
[[ "$(uname -s)-$(uname -m)" == Linux-x86_64 ]] || { echo 'Ce bootstrap cible Linux x86_64 ; configurer les chemins PEPSGOAL_* sur les autres hôtes.' >&2; exit 1; }
for tool in curl tar unzip python3 sha256sum; do command -v "$tool" >/dev/null || { echo "Outil requis : $tool" >&2; exit 1; }; done
source "$repo/scripts/dev/env.sh"
mkdir -p "$PEPSGOAL_DEV_HOME/toolchains" "$PEPSGOAL_DEV_HOME/logs" "$ANDROID_HOME/cmdline-tools"
tmp="$(mktemp -d "$PEPSGOAL_DEV_HOME/toolchains/download.XXXXXX")"
trap 'rm -rf "$tmp"' EXIT
if [[ ! -x "$JAVA_HOME/bin/javac" ]]; then
  [[ -z "${PEPSGOAL_JAVA_HOME:-}" ]] || { echo 'PEPSGOAL_JAVA_HOME doit pointer vers un JDK installé.' >&2; exit 1; }
  base='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz'
  curl -fL --retry 2 "$base" -o "$tmp/jdk.tar.gz"
  curl -fsSL --retry 2 "$base.sha256.txt" -o "$tmp/jdk.sha256"
  checksum="$(awk 'NR == 1 {print $1}' "$tmp/jdk.sha256")"
  [[ "$checksum" =~ ^[a-fA-F0-9]{64}$ ]] || exit 1
  printf '%s  %s\n' "$checksum" "$tmp/jdk.tar.gz" | sha256sum -c -
  tar -xzf "$tmp/jdk.tar.gz" -C "$PEPSGOAL_DEV_HOME/toolchains"
fi
if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
  curl -fL --retry 2 https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o "$tmp/sdk.zip"
  printf '%s  %s\n' 7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee "$tmp/sdk.zip" | sha256sum -c -
  unzip -q "$tmp/sdk.zip" -d "$tmp/sdk"
  mv "$tmp/sdk/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
if [[ "${1:-}" == --accept-sdk-licenses ]]; then
  # yes reçoit SIGPIPE après la fermeture de sdkmanager ; vérifier ce dernier.
  set +o pipefail
  yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses > "$PEPSGOAL_DEV_HOME/logs/sdk-licenses.log" 2>&1
  license_status=${PIPESTATUS[1]}
  set -o pipefail
  [[ "$license_status" == 0 ]] || { echo 'Échec des licences SDK : consulter le log local.' >&2; exit "$license_status"; }
else
  sdkmanager --sdk_root="$ANDROID_HOME" --licenses
fi
sdkmanager --sdk_root="$ANDROID_HOME" 'platform-tools' 'platforms;android-36' 'build-tools;35.0.0'
"$repo/scripts/dev/doctor.sh"
