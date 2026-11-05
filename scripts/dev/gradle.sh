#!/usr/bin/env bash
set -euo pipefail
repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$repo/scripts/dev/env.sh"
cd "$repo"
exec ./gradlew --console=plain --max-workers="${PEPSGOAL_WORKERS:-2}" "$@"
