#!/bin/bash
# Installs the debug APK with demo data and captures real screens for the website.
set +e
APK=app/build/outputs/apk/github/debug/app-github-debug.apk
adb install -r "$APK" || { echo "::error::install failed"; exit 1; }
adb shell pm grant com.myfit.tracker android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell am start -W -n com.myfit.tracker/.MainActivity --ez demo true
sleep 40
mkdir -p shots
shot() { adb shell am start -n com.myfit.tracker/.MainActivity --es open "shot:$1" >/dev/null; sleep ${2:-9}; adb shell screencap -p /sdcard/s.png; adb pull /sdcard/s.png "shots/$1.png" >/dev/null; echo "shot $1"; }
for r in home train progress exercises food nutritionist coach mealplans arena settings body deen; do shot $r; done
adb logcat -d -b crash > shots/crash.txt
ls -la shots
