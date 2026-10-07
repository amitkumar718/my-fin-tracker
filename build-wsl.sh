#!/bin/bash
# Build my-fin-tracker APK on WSL2
# Options: --stop (kill Gradle daemon before build)  --stacktrace (verbose Gradle output)
set -e

OPT_STOP=0
OPT_STACKTRACE=0
for arg in "$@"; do
    case "$arg" in
        --stop)       OPT_STOP=1 ;;
        --stacktrace) OPT_STACKTRACE=1 ;;
    esac
done

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG="$SCRIPT_DIR/build.log"
ENV_FILE="$SCRIPT_DIR/build-env.sh"
SIBLING_DIR="$SCRIPT_DIR/../mynutricheck"

# ── Bootstrap Gradle wrapper from sibling project ─────────────────────────────
if [ ! -f "$SCRIPT_DIR/gradlew" ]; then
    if [ ! -f "$SIBLING_DIR/gradlew" ]; then
        echo "✗ gradlew not found. Build mynutricheck first or copy gradlew manually."
        exit 1
    fi
    cp "$SIBLING_DIR/gradlew" "$SCRIPT_DIR/gradlew"
    cp "$SIBLING_DIR/gradlew.bat" "$SCRIPT_DIR/gradlew.bat"
    mkdir -p "$SCRIPT_DIR/gradle/wrapper"
    cp "$SIBLING_DIR/gradle/wrapper/gradle-wrapper.jar" "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar"
    cp "$SIBLING_DIR/gradle/wrapper/gradle-wrapper.properties" "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.properties"
    chmod +x "$SCRIPT_DIR/gradlew"
    echo "Bootstrapped gradlew from mynutricheck"
fi

# ── Load saved env ────────────────────────────────────────────────────────────
[ -f "$ENV_FILE" ] && source "$ENV_FILE"

# ── Java discovery ────────────────────────────────────────────────────────────
find_java() {
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then return 0; fi
    if command -v java &>/dev/null; then
        JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
        export JAVA_HOME; return 0
    fi
    for dir in /usr/lib/jvm/*/; do
        if [ -x "$dir/bin/java" ]; then JAVA_HOME="$dir"; export JAVA_HOME; return 0; fi
    done
    return 1
}

if ! find_java; then
    echo "✗ Could not find Java. Install: sudo apt-get install openjdk-21-jdk"
    exit 1
fi

echo "Java:        $JAVA_HOME"
echo "Android SDK: $SDK_DIR"

if [ ! -f "$SCRIPT_DIR/local.properties" ]; then
    echo "sdk.dir=$SDK_DIR" > "$SCRIPT_DIR/local.properties"
fi

if [ ! -d "$SDK_DIR" ]; then
    echo "✗ Android SDK not found at $SDK_DIR"
    echo "  Edit SDK_DIR in $ENV_FILE"
    exit 1
fi

# ── Clean stray R.java left behind by the Termux build-local.sh ───────────────
# aapt2 in build-local.sh writes R.java into app/src/main/java; AGP generates
# its own and will fail with "duplicate class" if that file is present.
STRAY_R="$SCRIPT_DIR/app/src/main/java/com/ldsa/myfintracker/R.java"
[ -f "$STRAY_R" ] && { rm "$STRAY_R"; echo "Removed stray R.java from Termux build"; }

# ── Build ─────────────────────────────────────────────────────────────────────
echo "Building... log → $LOG"
cd "$SCRIPT_DIR"

[ "$OPT_STOP" = "1" ] && { echo "Stopping Gradle daemon..."; ./gradlew --stop 2>/dev/null || true; }

GRADLE_ARGS="assembleDebug"
[ "$OPT_STACKTRACE" = "1" ] && GRADLE_ARGS="$GRADLE_ARGS --stacktrace"

./gradlew $GRADLE_ARGS 2>&1 | tee "$LOG"

APK="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
    echo ""
    echo "✓ APK: $APK"
    echo "Install: adb install -r $APK"
else
    echo "✗ Build failed — check $LOG"
    exit 1
fi
