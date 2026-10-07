#!/usr/bin/env bash
# Build APK, start AVD if not running, install and launch my-fin-tracker.
# Usage: ./build-run.sh [avd-name]

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG="$SCRIPT_DIR/build-run.log"
APK="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
AVD_NAME="${1:-Pixel_7}"
PACKAGE="com.ldsa.myfintracker"
MAIN_ACTIVITY="$PACKAGE/.ui.MainActivity"

exec > >(tee "$LOG") 2>&1

# ── Build ─────────────────────────────────────────────────────────────────────
echo "Building..."
cd "$SCRIPT_DIR"
bash build-wsl.sh

# ── Start emulator if not running ─────────────────────────────────────────────
if ! adb devices | grep -q "emulator"; then
    echo "Starting emulator $AVD_NAME..."
    powershell.exe -Command "Set-Location \$env:USERPROFILE; Start-Process \"\$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe\" -ArgumentList '-avd','$AVD_NAME','-no-snapshot-load'"

    echo "Waiting for emulator to appear..."
    until adb devices | grep -q "emulator"; do
        sleep 2
    done

    echo "Waiting for boot..."
    until adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n' | grep -q "^1$"; do
        sleep 3
    done
    echo "Emulator ready"
else
    echo "Emulator already running"
fi

# ── Install ───────────────────────────────────────────────────────────────────
echo "Installing $APK..."
adb install -r "$APK"

# SMS inbox feature requires READ_SMS; grant up-front so the app has data to show.
adb shell pm grant "$PACKAGE" android.permission.READ_SMS 2>/dev/null || true

# ── Launch ────────────────────────────────────────────────────────────────────
echo "Launching my-fin-tracker..."
adb shell am start -n "$MAIN_ACTIVITY"
