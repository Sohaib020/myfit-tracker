#!/bin/bash
# Installs the debug APK on the emulator, opens the dashboard and each tab, and reports any crash.
set +e
APK=${SMOKE_APK:-app/build/outputs/apk/github/debug/app-github-debug.apk}
adb install -r "$APK" || { echo "::error title=Smoke::install failed"; exit 1; }
adb logcat -c
adb shell am start -W -n com.myfit.tracker/.MainActivity --ez smoke true
sleep 25
adb shell screencap -p /sdcard/s1.png; adb pull /sdcard/s1.png smoke-dashboard.png
# tap through the dock tabs (bottom of a 1080x2400 screen)
for x in 330 540 750 950 120; do adb shell input tap $x 2230; sleep 6; done
adb shell screencap -p /sdcard/s2.png; adb pull /sdcard/s2.png smoke-last.png
adb logcat -d > logcat.txt
adb logcat -d -b crash > crash.txt
PID=$(adb shell pidof com.myfit.tracker)
echo "pid after run: $PID"
if [ -s crash.txt ] || grep -q "FATAL EXCEPTION" logcat.txt; then
  { grep -A40 "FATAL EXCEPTION" logcat.txt; cat crash.txt; } | head -80 | sed ':a;N;$!ba;s/%/%25/g;s/\r/%0D/g;s/\n/%0A/g' | sed 's/^/::error title=App crash::/'
  exit 1
fi
if [ -z "$PID" ]; then
  grep -E "AndroidRuntime|myfit|DEBUG|libc" logcat.txt | tail -60 | sed ':a;N;$!ba;s/%/%25/g;s/\n/%0A/g' | sed 's/^/::error title=App not running::/'
  exit 1
fi
echo "Smoke test passed"
