#!/bin/bash
# usage: gr.sh <tasks...> — retries on 429
cd /home/user/pepsgoal-android
for i in 1 2 3 4 5 6; do
  timeout 1200 ./gradlew -q --no-daemon --max-workers=2 "$@" > /tmp/out.txt 2>&1 && { echo OK; exit 0; }
  if grep -q "429" /tmp/out.txt; then echo retry429; sleep 15; continue; fi
  break
done
grep -v "JAVA_TOOL\|^w: " /tmp/out.txt | head -60
