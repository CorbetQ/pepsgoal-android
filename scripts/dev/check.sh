#!/usr/bin/env bash
set -euo pipefail
repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
case "${1:-help}" in
  core) tasks=(:core:test) ;;
  ui) tasks=(:app:compileGithubDebugKotlin) ;;
  apk) tasks=(:app:assembleGithubDebug) ;;
  wear) tasks=(:wear:assembleDebug) ;;
  tooling) tasks=(:benchmark:compileBenchmarkKotlin :baselineprofile:compileNonMinifiedReleaseKotlin) ;;
  lint) tasks=(:app:lintGithubDebug :wear:lintDebug) ;;
  *) echo 'Usage: scripts/dev/check.sh {core|ui|apk|wear|tooling|lint}'; exit 2 ;;
esac
exec "$repo/scripts/dev/gradle.sh" "${tasks[@]}"
