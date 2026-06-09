#!/bin/bash
# Start both phone and Wear OS emulators with DNS
# Run on the HOST (macOS) before using the devcontainer

set -e

EMULATOR="${ANDROID_HOME:-$HOME/Library/Android/sdk}/emulator/emulator"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"

PHONE_AVD="${1:-Pixel_10_Pro_XL}"
WEAR_AVD="${2:-Wear_OS_Large_Round}"

# Kill any existing emulators
echo "=== Stopping existing emulators ==="
"$ADB" devices | grep "emulator" | awk '{print $1}' | while read -r serial; do
    "$ADB" -s "$serial" emu kill 2>/dev/null || true
done

echo "Waiting for emulators to stop..."
for i in $(seq 1 30); do
    DEVICES=$("$ADB" devices | grep -c "emulator" || true)
    if [ "$DEVICES" -eq 0 ]; then
        break
    fi
    sleep 1
done

"$ADB" kill-server
sleep 1
"$ADB" start-server
sleep 1

echo "=== Starting phone emulator ($PHONE_AVD) ==="
nohup "$EMULATOR" -avd "$PHONE_AVD" -no-snapshot-load -dns-server 8.8.8.8,8.8.4.4 -gpu host > /tmp/emulator-phone.log 2>&1 &
PHONE_PID=$!
echo "Phone emulator PID: $PHONE_PID"

sleep 8

echo "=== Starting Wear OS emulator ($WEAR_AVD) ==="
nohup "$EMULATOR" -avd "$WEAR_AVD" -no-snapshot-load -dns-server 8.8.8.8,8.8.4.4 -gpu host > /tmp/emulator-wear.log 2>&1 &
WEAR_PID=$!
echo "Wear emulator PID: $WEAR_PID"

echo ""
echo "Waiting for devices to appear..."
for i in $(seq 1 60); do
    DEVICES=$("$ADB" devices | grep -c "device$" || true)
    if [ "$DEVICES" -ge 2 ]; then
        break
    fi
    sleep 2
done

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

echo ""
echo "Waiting for both to boot..."
for i in $(seq 1 60); do
    PB=$("$ADB" -s "$PHONE_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    WB=$("$ADB" -s "$WEAR_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    if [ "$PB" = "1" ] && [ "$WB" = "1" ]; then
        break
    fi
    sleep 3
done

# Write serials to file for devcontainer-connect.sh to use
echo "$PHONE_SERIAL" > /tmp/emulator-phone-serial
echo "$WEAR_SERIAL" > /tmp/emulator-wear-serial

echo ""
echo "Both emulators are ready."
echo "  Phone: $PHONE_SERIAL"
echo "  Watch: $WEAR_SERIAL"
echo ""
echo "=== To connect from devcontainer: ==="
echo "  scripts/devcontainer-connect.sh"
echo ""
echo "=== To stop emulators: ==="
echo "  kill $PHONE_PID $WEAR_PID"
