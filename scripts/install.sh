#!/bin/bash
# Build and install both apps on running emulators
# Run on HOST when emulators are already started via start-emulator.sh

set -e

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"

echo "=== Building ==="
./gradlew assembleDebug

echo ""
echo "Detecting devices..."
PHONE_SERIAL=""
WEAR_SERIAL=""
while read -r line; do
    SERIAL=$(echo "$line" | awk '{print $1}')
    CHARCS=$("$ADB" -s "$SERIAL" shell getprop ro.build.characteristics 2>/dev/null | tr -d '\r')
    if echo "$CHARCS" | grep -q "watch"; then
        WEAR_SERIAL="$SERIAL"
        echo "  Watch: $SERIAL"
    else
        PHONE_SERIAL="$SERIAL"
        echo "  Phone: $SERIAL"
    fi
done < <("$ADB" devices | grep "device$")

if [ -z "$PHONE_SERIAL" ] || [ -z "$WEAR_SERIAL" ]; then
    echo "ERROR: Need both emulators running. Start with: scripts/start-emulator.sh"
    exit 1
fi

echo ""
echo "=== Installing phone app on $PHONE_SERIAL ==="
"$ADB" -s "$PHONE_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk

echo ""
echo "=== Installing Wear OS app on $WEAR_SERIAL ==="
"$ADB" -s "$WEAR_SERIAL" install -r wear/build/outputs/apk/debug/wear-debug.apk

echo ""
echo "=== Launching ==="
"$ADB" -s "$PHONE_SERIAL" shell am start -n com.nimbleflux.glucosesync/com.nimbleflux.glucosesync.app.ui.MainActivity
"$ADB" -s "$WEAR_SERIAL" shell am start -n com.nimbleflux.glucosesync/com.nimbleflux.glucosesync.wear.ui.MainActivity

echo ""
echo "Done!"
