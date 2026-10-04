#!/usr/bin/env bash
# Streams AirPlay request-capture lines from a TV running a debug build.
# Usage: scripts/capture.sh <tv-ip> <outfile>
set -euo pipefail
TV="$1:5555"
OUT="$2"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
"$ADB" connect "$TV" >/dev/null
"$ADB" -s "$TV" logcat -c
"$ADB" -s "$TV" logcat -v time AirPlayNative:V AirPlayVideoPlayer:V AirPlayService:I '*:S' \
  | grep --line-buffered -E 'AIRCAP|AirPlay Video' | tee "$OUT"
