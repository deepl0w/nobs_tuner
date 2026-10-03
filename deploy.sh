#!/usr/bin/env bash

# Nobs Tuner - Deploy Script
# Deploys app to connected device or emulator

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

cd "$(dirname "${BASH_SOURCE[0]}")"

# sed rather than `grep -oP`, which only exists in GNU grep.
gradle_value() {
    sed -nE 's/.*'"$1"'[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/p' app/build.gradle.kts | head -1
}

APP_ID=$(gradle_value applicationId)
NAMESPACE=$(gradle_value namespace)
DEBUG_SUFFIX=$(gradle_value applicationIdSuffix)

if [ -z "$APP_ID" ] || [ -z "$NAMESPACE" ]; then
    echo -e "${RED}✗${NC} Could not read applicationId/namespace from app/build.gradle.kts"
    exit 1
fi

# Without a keystore AGP leaves the release APK unsigned and names it so.
release_apk_path() {
    local dir="app/build/outputs/apk/release"
    if [ -f "$dir/app-release-unsigned.apk" ] && [ ! -f "$dir/app-release.apk" ]; then
        echo "$dir/app-release-unsigned.apk"
    else
        echo "$dir/app-release.apk"
    fi
}

echo -e "${BLUE}=====================================${NC}"
echo -e "${BLUE}Nobs Tuner - Deploy Script${NC}"
echo -e "${BLUE}=====================================${NC}"
echo ""

# Parse arguments
BUILD_TYPE="debug"
FORCE_BUILD=false
LAUNCH=false
FRESH=false
LOGS_ONLY=false
SERIAL=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --release)
            BUILD_TYPE="release"
            shift
            ;;
        --build|-b)
            FORCE_BUILD=true
            shift
            ;;
        --launch|-l)
            LAUNCH=true
            shift
            ;;
        --fresh)
            FRESH=true
            shift
            ;;
        --logs)
            LOGS_ONLY=true
            shift
            ;;
        --device|-s)
            SERIAL="$2"
            shift 2
            ;;
        --help|-h)
            echo "Usage: ./deploy.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --release        Deploy release version (default: debug)"
            echo "  --build, -b      Force rebuild before deploying"
            echo "  --launch, -l     Launch app and follow its logs"
            echo "  --fresh          Uninstall first (wipes saved tunings)"
            echo "  --logs           Only follow the logs of the installed app"
            echo "  --device, -s     Target a specific device serial"
            echo "  --help, -h       Show this help message"
            echo ""
            echo "Examples:"
            echo "  ./deploy.sh                  # Deploy existing debug APK"
            echo "  ./deploy.sh --build          # Build and deploy debug"
            echo "  ./deploy.sh --release -l     # Deploy release and launch"
            echo "  ./deploy.sh --logs           # Just watch the app's logs"
            echo ""
            echo "Installs over the top by default so custom tunings and"
            echo "favourites survive. Use --fresh to start from nothing."
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Run './deploy.sh --help' for usage information"
            exit 1
            ;;
    esac
done

ADB=(adb)
if [ -n "$SERIAL" ]; then
    ADB=(adb -s "$SERIAL")
fi

if [ "$BUILD_TYPE" = "release" ]; then
    PACKAGE="$APP_ID"
else
    PACKAGE="${APP_ID}${DEBUG_SUFFIX}"
fi
COMPONENT="$PACKAGE/$NAMESPACE.MainActivity"

# Resolved fresh at each use rather than stored: a build in between can change
# which of the two release names exists.
apk_path() {
    if [ "$BUILD_TYPE" = "release" ]; then
        release_apk_path
    else
        echo "app/build/outputs/apk/debug/app-debug.apk"
    fi
}

# Follows logcat for just this app. The app has no log tag of its own, so the
# filter is by process id, which also picks up anything the framework says
# about it.
app_pid() {
    "${ADB[@]}" shell pidof -s "$PACKAGE" 2>/dev/null | tr -d '\r'
}

# With --wait, give a cold start time to get as far as a running process before
# giving up: `am start -W` returns once the activity is drawn, but on a slow
# emulator the pid can still take a moment to show up.
follow_logs() {
    local pid
    pid=$(app_pid)
    if [ -z "$pid" ] && [ "${1:-}" = "--wait" ]; then
        for _ in {1..10}; do
            sleep 0.5
            pid=$(app_pid)
            [ -n "$pid" ] && break
        done
    fi
    if [ -z "$pid" ]; then
        echo -e "${YELLOW}⚠${NC} $PACKAGE is not running; showing errors only"
        "${ADB[@]}" logcat "*:E"
    else
        echo "Following pid $pid (Ctrl+C to stop)"
        echo ""
        "${ADB[@]}" logcat --pid="$pid" "*:V"
    fi
}

# Check for connected devices. `adb -s <serial> devices` still lists every
# device, so when a serial is given the check is that *it* is there, not how
# many others are.
echo "Checking for connected devices..."
DEVICE_LIST=$(adb devices | grep -v "List" | grep "device$" || true)
DEVICE_COUNT=$(echo "$DEVICE_LIST" | grep -c "device" || true)

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo -e "${RED}✗ No device found!${NC}"
    echo ""
    echo "Please connect a device or start an emulator:"
    echo "  # List available emulators"
    echo "  emulator -list-avds"
    echo ""
    echo "  # Start an emulator"
    echo "  emulator -avd <name> &"
    exit 1
elif [ -n "$SERIAL" ]; then
    if echo "$DEVICE_LIST" | awk '{print $1}' | grep -qx "$SERIAL"; then
        echo -e "${GREEN}✓ Using device: $SERIAL${NC}"
    else
        echo -e "${RED}✗ Device '$SERIAL' is not connected${NC}"
        echo "$DEVICE_LIST" | sed 's/^/    /'
        exit 1
    fi
elif [ "$DEVICE_COUNT" -eq 1 ]; then
    DEVICE=$(echo "$DEVICE_LIST" | awk '{print $1}')
    echo -e "${GREEN}✓ Found device: $DEVICE${NC}"
else
    echo -e "${YELLOW}⚠ Multiple devices found:${NC}"
    echo "$DEVICE_LIST" | sed 's/^/    /'
    echo ""
    echo "Pass --device <serial> to pick one, e.g.:"
    echo "  ./deploy.sh --device $(echo "$DEVICE_LIST" | head -1 | awk '{print $1}')"
    exit 1
fi
echo ""

if [ "$LOGS_ONLY" = true ]; then
    follow_logs
    exit 0
fi

# Build if forced or APK doesn't exist
if [ "$FORCE_BUILD" = true ] || [ ! -f "$(apk_path)" ]; then
    echo -e "${YELLOW}Building app...${NC}"
    BUILD_ARGS=()
    [ "$BUILD_TYPE" = "release" ] && BUILD_ARGS+=(--release)
    [ -n "$SERIAL" ] && BUILD_ARGS+=(--device "$SERIAL")
    ./build.sh "${BUILD_ARGS[@]}"
    echo ""
fi

APK_PATH=$(apk_path)

if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}✗ APK not found at: $APK_PATH${NC}"
    echo "Run with --build flag to build first"
    exit 1
fi

case "$APK_PATH" in
    *-unsigned.apk)
        echo -e "${RED}✗ The release APK is unsigned, and adb cannot install one.${NC}"
        echo "Configure signing in keystore.properties or the ANDROID_KEYSTORE_*"
        echo "environment variables — see docs/PLAY_STORE.md."
        exit 1
        ;;
esac

APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
echo "Deploying APK:"
echo "  Path: $APK_PATH"
echo "  Size: $APK_SIZE"
echo "  Type: $BUILD_TYPE"
echo "  Package: $PACKAGE"
echo ""

# Only remove the existing install when asked. A plain reinstall keeps the
# user's custom tunings and favourites, which is almost always what you want
# when iterating.
if [ "$FRESH" = true ]; then
    echo -e "${YELLOW}Removing existing installation...${NC}"
    "${ADB[@]}" uninstall "$PACKAGE" 2>/dev/null || true
    echo ""
fi

echo -e "${YELLOW}Installing app...${NC}"
"${ADB[@]}" install -r "$APK_PATH"
echo -e "${GREEN}✓ App installed successfully${NC}"
echo ""

echo -e "${YELLOW}Granting microphone permission...${NC}"
# Some manufacturers (OnePlus/Oppo among them) refuse GRANT_RUNTIME_PERMISSIONS
# to the adb shell user, so this can fail. Check rather than assume: claiming
# success and leaving a silent permission dialog on the device wastes more
# time than saying plainly that it has to be tapped.
"${ADB[@]}" shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO >/dev/null 2>&1 || true
if "${ADB[@]}" shell dumpsys package "$PACKAGE" 2>/dev/null \
    | tr -d '\r' | grep -q "RECORD_AUDIO: granted=true"; then
    echo -e "${GREEN}✓${NC} Microphone permission granted"
else
    echo -e "${YELLOW}⚠${NC} Could not grant the microphone permission from adb."
    echo "  Accept the prompt on the device; this phone's OEM blocks adb grants."
fi
echo ""

if [ "$LAUNCH" = true ]; then
    echo -e "${YELLOW}Launching app...${NC}"
    "${ADB[@]}" logcat -c
    "${ADB[@]}" shell am start -W -n "$COMPONENT"
    echo -e "${GREEN}✓ App launched${NC}"
    echo ""
    follow_logs --wait
else
    echo -e "${GREEN}=====================================${NC}"
    echo -e "${GREEN}Deployment Complete!${NC}"
    echo -e "${GREEN}=====================================${NC}"
    echo ""
    echo "To launch the app:"
    echo "  adb shell am start -n $COMPONENT"
    echo ""
    echo "To view logs:"
    echo "  ./deploy.sh --logs"
    echo ""
    echo "To uninstall:"
    echo "  adb uninstall $PACKAGE"
fi
