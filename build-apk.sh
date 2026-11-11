#!/bin/bash
set -e

echo "=== 1. Building Web Frontend ==="
npm run build

echo "=== 2. Syncing Capacitor Assets ==="
npx cap sync android

echo "=== 3. Compiling Android Debug APK ==="
cd android
chmod +x ./gradlew
./gradlew assembleDebug

echo "=== SUCCESS ==="
echo "APK: android/app/build/outputs/apk/debug/app-debug.apk"

