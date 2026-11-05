#!/usr/bin/env bash
# À sourcer. Aucune modification du profil shell global ou des secrets.
_peps_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export PEPSGOAL_DEV_HOME="${PEPSGOAL_DEV_HOME:-$(dirname "$_peps_root")/.pepsgoal}"
export JAVA_HOME="${PEPSGOAL_JAVA_HOME:-$PEPSGOAL_DEV_HOME/toolchains/jdk-17.0.21+9}"
export ANDROID_HOME="${PEPSGOAL_ANDROID_HOME:-$PEPSGOAL_DEV_HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="${PEPSGOAL_GRADLE_HOME:-$PEPSGOAL_DEV_HOME/gradle}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$PATH"
# Java ne lit pas HTTPS_PROXY. Conserver le proxy hérité et le magasin de CA système.
if [[ -f /etc/ssl/certs/java/cacerts && "${JAVA_TOOL_OPTIONS:-}" != *javax.net.ssl.trustStore=* ]]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
if [[ -n "${HTTPS_PROXY:-${https_proxy:-}}" && "${JAVA_TOOL_OPTIONS:-}" != *https.proxyHost=* ]]; then
  _peps_proxy_flags="$(python3 - <<'PY'
import os
from urllib.parse import urlsplit
p = urlsplit(os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy', ''))
if p.hostname and p.port and all(c.isalnum() or c in '.-' for c in p.hostname):
    print(f'-Dhttps.proxyHost={p.hostname} -Dhttps.proxyPort={p.port} '
          f'-Dhttp.proxyHost={p.hostname} -Dhttp.proxyPort={p.port} '
          '-Dhttp.nonProxyHosts=localhost|127.*|[::1]')
PY
)"
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} $_peps_proxy_flags"
fi
unset _peps_root _peps_proxy_flags
