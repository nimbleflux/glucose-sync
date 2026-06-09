#!/bin/bash
# Run INSIDE the devcontainer to connect to host emulators
# Prerequisites: run scripts/start-emulator.sh on the host first

set -e

ADB="adb"
APK_DIR="/workspaces/medtrum"

echo "=== Starting ADB proxy to host ==="
"$ADB" kill-server 2>/dev/null || true
socat TCP-LISTEN:5037,reuseaddr,fork TCP:host.docker.internal:5037 &
SOCAT_PID=$!
sleep 3

echo "Waiting for devices..."
for i in $(seq 1 30); do
    DEVICES=$("$ADB" devices 2>/dev/null | grep -c "device$" || true)
    if [ "$DEVICES" -ge 2 ]; then
        break
    fi
    sleep 2
done

"$ADB" devices

echo ""
echo "Detecting phone vs watch..."
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

if [ -z "$PHONE_SERIAL" ]; then
    echo "ERROR: Could not detect phone emulator"
    exit 1
fi
if [ -z "$WEAR_SERIAL" ]; then
    echo "ERROR: Could not detect Wear OS emulator"
    exit 1
fi

echo ""
echo "=== Building ==="
./gradlew assembleDebug

echo ""
echo "=== Installing phone app on $PHONE_SERIAL ==="
"$ADB" -s "$PHONE_SERIAL" install -r "$APK_DIR/app/build/outputs/apk/debug/app-debug.apk"

echo ""
echo "=== Installing Wear OS app on $WEAR_SERIAL ==="
"$ADB" -s "$WEAR_SERIAL" install -r "$APK_DIR/wear/build/outputs/apk/debug/wear-debug.apk"

echo ""
echo "=== Launching apps ==="
"$ADB" -s "$PHONE_SERIAL" shell am start -n com.nimbleflux.glucosesync/com.nimbleflux.glucosesync.app.ui.MainActivity
"$ADB" -s "$WEAR_SERIAL" shell am start -n com.nimbleflux.glucosesync/com.nimbleflux.glucosesync.wear.ui.MainActivity

echo ""
echo "Done! Phone app on $PHONE_SERIAL, Wear app on $WEAR_SERIAL"
echo "ADB proxy PID: $SOCAT_PID (kill to stop)"
