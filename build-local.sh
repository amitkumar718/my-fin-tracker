#!/usr/bin/env sh
set -eu

SRC_DIR="app/src/main/java"
RES_DIR="app/src/main/res"
MANIFEST="app/src/main/AndroidManifest.xml"

BUILD_DIR="build/local"
COMPILED_RES_DIR="$BUILD_DIR/compiled_res"
CLASS_DIR="$BUILD_DIR/classes"
DEX_DIR="$BUILD_DIR/dex"
APK_UNSIGNED="$BUILD_DIR/myfintracker-unsigned.apk"
APK_SIGNED="$BUILD_DIR/myfintracker-debug.apk"

KEYSTORE_DIR=".local-keystore"
KEYSTORE="$KEYSTORE_DIR/myfintracker-debug.keystore"
KEY_ALIAS="myfintracker"
KEY_PASS="myfintracker_debug"

ANDROID_JAR="${ANDROID_JAR:-$HOME/android-sdk/platforms/android-35/android.jar}"
FRAMEWORK_RES="${FRAMEWORK_RES:-$HOME/android-sdk/platforms/android-13/android.jar}"

MIN_API=21
TARGET_API=34
VERSION_CODE=1
VERSION_NAME="1.0"
PACKAGE="com.ldsa.myfintracker"

echo "=== Checking tools ==="
for TOOL in aapt aapt2 javac d8 apksigner keytool; do
    if ! command -v "$TOOL" >/dev/null 2>&1; then
        echo "ERROR: $TOOL not found."
        exit 1
    fi
done

if [ ! -f "$ANDROID_JAR" ]; then
    echo "ERROR: Android jar not found: $ANDROID_JAR"
    exit 1
fi

if [ ! -f "$FRAMEWORK_RES" ]; then
    echo "ERROR: Framework res jar not found: $FRAMEWORK_RES"
    exit 1
fi

echo "=== Preparing build directories ==="
rm -rf "$BUILD_DIR"
mkdir -p "$COMPILED_RES_DIR" "$CLASS_DIR" "$DEX_DIR"

echo "=== Step 1: Compiling resources ==="
find "$RES_DIR" -type f \( -name "*.xml" -o -name "*.png" \) | while read -r RES_FILE; do
    aapt2 compile "$RES_FILE" -o "$COMPILED_RES_DIR"
done

echo "=== Step 2: Linking resources ==="
aapt2 link \
    -o "$APK_UNSIGNED" \
    -I "$FRAMEWORK_RES" \
    --manifest "$MANIFEST" \
    --java "$SRC_DIR" \
    --target-sdk-version "$TARGET_API" \
    --min-sdk-version "$MIN_API" \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION_NAME" \
    "$COMPILED_RES_DIR"/*.flat

echo "=== Step 3: Compiling Java sources ==="
find "$SRC_DIR" -name "*.java" > "$BUILD_DIR/sources.list"
javac \
    -source 8 \
    -target 8 \
    -classpath "$ANDROID_JAR" \
    -d "$CLASS_DIR" \
    @"$BUILD_DIR/sources.list"

echo "=== Step 4: Dexing with d8 ==="
find "$CLASS_DIR" -name "*.class" > "$BUILD_DIR/classes.list"
d8 \
    --min-api "$MIN_API" \
    --classpath "$ANDROID_JAR" \
    --output "$DEX_DIR" \
    @"$BUILD_DIR/classes.list"

echo "=== Step 5: Adding dex to APK ==="
(
    cd "$BUILD_DIR"
    cp "dex/classes.dex" .
    aapt add "$(basename "$APK_UNSIGNED")" classes.dex >/dev/null
    rm classes.dex
)

echo "=== Step 6: Signing APK ==="
if [ ! -f "$KEYSTORE" ]; then
    mkdir -p "$KEYSTORE_DIR"
    keytool -genkeypair \
        -keystore "$KEYSTORE" \
        -alias "$KEY_ALIAS" \
        -keypass "$KEY_PASS" \
        -storepass "$KEY_PASS" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=myfintracker Debug, OU=Debug, O=ldsa, L=Unknown, ST=Unknown, C=US" \
        -noprompt
fi

apksigner sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "pass:$KEY_PASS" \
    --key-pass "pass:$KEY_PASS" \
    --out "$APK_SIGNED" \
    "$APK_UNSIGNED"

echo ""
echo "============================================"
echo "  BUILD SUCCESSFUL"
echo "  APK: $APK_SIGNED"
echo "============================================"
echo ""
echo "Install:"
echo "  adb install -r $APK_SIGNED"
echo ""
echo "Grant permissions:"
echo "  adb shell pm grant $PACKAGE android.permission.READ_SMS"
