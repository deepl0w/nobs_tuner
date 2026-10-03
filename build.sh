#!/usr/bin/env bash

# Nobs Tuner - Build Script
# Builds the Android app without Android Studio

set -e  # Exit on error

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

cd "$(dirname "${BASH_SOURCE[0]}")"

# Read the identifiers out of the Gradle config rather than hardcoding them, so
# renaming applicationId before publishing does not silently break the scripts.
# sed rather than `grep -oP`, which only exists in GNU grep.
gradle_value() {
    sed -n "s/.*$1[[:space:]]*=[[:space:]]*\"\([^\"]*\)\".*/\1/p" app/build.gradle.kts | head -1
}

APP_ID=$(gradle_value applicationId)
NAMESPACE=$(gradle_value namespace)
DEBUG_SUFFIX=$(gradle_value applicationIdSuffix)

if [ -z "$APP_ID" ] || [ -z "$NAMESPACE" ]; then
    echo -e "${RED}✗${NC} Could not read applicationId/namespace from app/build.gradle.kts"
    exit 1
fi

# Without a keystore AGP leaves the release APK unsigned and names it
# accordingly, so the output path is not known until after the build.
release_apk_path() {
    local dir="app/build/outputs/apk/release"
    if [ -f "$dir/app-release-unsigned.apk" ] && [ ! -f "$dir/app-release.apk" ]; then
        echo "$dir/app-release-unsigned.apk"
    else
        echo "$dir/app-release.apk"
    fi
}

echo -e "${BLUE}=====================================${NC}"
echo -e "${BLUE}Nobs Tuner - Build Script${NC}"
echo -e "${BLUE}=====================================${NC}"
echo ""

# Parse arguments
BUILD_TYPE="debug"
BUNDLE=false
INSTALL=false
RUN=false
SERIAL=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --release)
            BUILD_TYPE="release"
            shift
            ;;
        --bundle)
            BUNDLE=true
            BUILD_TYPE="release"
            shift
            ;;
        --install|-i)
            INSTALL=true
            shift
            ;;
        --run|-r)
            RUN=true
            INSTALL=true
            shift
            ;;
        --device|-s)
            SERIAL="$2"
            shift 2
            ;;
        --help|-h)
            echo "Usage: ./build.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --release        Build release version (default: debug)"
            echo "  --bundle         Build the Play Store AAB (implies --release)"
            echo "  --install, -i    Install APK after building"
            echo "  --run, -r        Install and run app after building"
            echo "  --device, -s     Target a specific device serial"
            echo "  --help, -h       Show this help message"
            echo ""
            echo "Examples:"
            echo "  ./build.sh                    # Build debug APK"
            echo "  ./build.sh --release          # Build release APK"
            echo "  ./build.sh --install          # Build and install debug"
            echo "  ./build.sh --release --run    # Build release, install and run"
            echo "  ./build.sh --bundle           # Build the AAB to upload to Play"
            echo ""
            echo "Release signing comes from keystore.properties or the"
            echo "ANDROID_KEYSTORE_* environment variables. Without either, the"
            echo "build still succeeds but the output is unsigned."
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Run './build.sh --help' for usage information"
            exit 1
            ;;
    esac
done

ADB=(adb)
if [ -n "$SERIAL" ]; then
    ADB=(adb -s "$SERIAL")
fi

# Check if device is connected (if install requested)
if [ "$INSTALL" = true ]; then
    echo "Checking for connected devices..."
    DEVICE_LIST=$(adb devices | grep -v "List" | grep "device$" || true)
    DEVICES=$(echo "$DEVICE_LIST" | grep -c "device" || true)
    if [ "$DEVICES" -eq 0 ]; then
        echo -e "${RED}✗${NC} No device found!"
        echo "Please connect a device or start an emulator"
        exit 1
    elif [ -n "$SERIAL" ]; then
        # A serial that is not attached would otherwise only blow up on
        # `adb install`, after the whole build.
        if echo "$DEVICE_LIST" | awk '{print $1}' | grep -qx "$SERIAL"; then
            echo -e "${GREEN}✓${NC} Using device: $SERIAL"
        else
            echo -e "${RED}✗${NC} Device '$SERIAL' is not connected:"
            echo "$DEVICE_LIST" | sed 's/^/    /'
            exit 1
        fi
    elif [ "$DEVICES" -gt 1 ]; then
        # adb refuses to guess, so fail here with something actionable rather
        # than letting the install blow up after a full build.
        echo -e "${RED}✗${NC} More than one device is connected:"
        echo "$DEVICE_LIST" | sed 's/^/    /'
        echo ""
        echo "Pick one with --device <serial>, e.g.:"
        echo "  ./build.sh --install --device $(echo "$DEVICE_LIST" | head -1 | awk '{print $1}')"
        exit 1
    else
        echo -e "${GREEN}✓${NC} Found $DEVICES device(s)"
    fi
    echo ""
fi

# Build
if [ "$BUNDLE" = true ]; then
    echo -e "${YELLOW}Building Android App Bundle (release)...${NC}"
    ./gradlew :app:bundleRelease
    ARTIFACT="app/build/outputs/bundle/release/app-release.aab"
elif [ "$BUILD_TYPE" = "release" ]; then
    echo -e "${YELLOW}Building Android app (release)...${NC}"
    ./gradlew :app:assembleRelease
    ARTIFACT=$(release_apk_path)
else
    echo -e "${YELLOW}Building Android app (debug)...${NC}"
    ./gradlew :app:assembleDebug
    ARTIFACT="app/build/outputs/apk/debug/app-debug.apk"
fi
echo -e "${GREEN}✓${NC} Android app built successfully"
echo ""

# Artifact info
if [ -f "$ARTIFACT" ]; then
    ARTIFACT_SIZE=$(du -h "$ARTIFACT" | cut -f1)
    echo -e "${GREEN}=====================================${NC}"
    echo -e "${GREEN}Build Complete!${NC}"
    echo -e "${GREEN}=====================================${NC}"
    echo "Location: $ARTIFACT"
    echo "Size: $ARTIFACT_SIZE"
    echo ""
else
    echo -e "${RED}✗ Artifact not found at expected location${NC}"
    exit 1
fi

# An AAB cannot be installed with adb; bundletool has to split it first.
if [ "$BUNDLE" = true ]; then
    if [ "$INSTALL" = true ] || [ "$RUN" = true ]; then
        echo -e "${YELLOW}⚠${NC} A bundle cannot be installed directly with adb."
        echo "  Use ./build.sh --release --install for a device-installable APK,"
        echo "  or see docs/PLAY_STORE.md for testing an AAB with bundletool."
        echo ""
    fi
    echo -e "${BLUE}=====================================${NC}"
    echo "Next steps:"
    echo "• Upload $ARTIFACT to the Play Console"
    echo "• Checklist: docs/PLAY_STORE.md"
    echo -e "${BLUE}=====================================${NC}"
    exit 0
fi

# The debug build carries an applicationId suffix, so the installed package is
# not the same as the one in the release build.
if [ "$BUILD_TYPE" = "release" ]; then
    PACKAGE="$APP_ID"
else
    PACKAGE="${APP_ID}${DEBUG_SUFFIX}"
fi
COMPONENT="$PACKAGE/$NAMESPACE.MainActivity"

case "$ARTIFACT" in
    *-unsigned.apk)
        if [ "$INSTALL" = true ]; then
            echo -e "${RED}✗${NC} The release APK is unsigned, and adb cannot install one."
            echo "  Configure signing in keystore.properties or the ANDROID_KEYSTORE_*"
            echo "  environment variables — see docs/PLAY_STORE.md."
            echo "  The unsigned APK itself is at $ARTIFACT."
            exit 1
        fi
        echo -e "${YELLOW}⚠${NC} No keystore configured, so this APK is unsigned."
        echo "  Sign it before installing or uploading — see docs/PLAY_STORE.md."
        echo ""
        ;;
esac

# Install if requested
if [ "$INSTALL" = true ]; then
    echo -e "${YELLOW}Installing APK...${NC}"
    "${ADB[@]}" install -r "$ARTIFACT"
    echo -e "${GREEN}✓${NC} App installed successfully"
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
fi

# Run if requested
if [ "$RUN" = true ]; then
    echo -e "${YELLOW}Launching app...${NC}"
    "${ADB[@]}" shell am start -n "$COMPONENT"
    echo -e "${GREEN}✓${NC} App launched successfully"
    echo ""
fi

echo -e "${BLUE}=====================================${NC}"
echo "Next steps:"
if [ "$INSTALL" = false ]; then
    case "$ARTIFACT" in
        *-unsigned.apk) echo "• Sign it first — adb cannot install an unsigned APK" ;;
        *)              echo "• Install: adb install -r $ARTIFACT" ;;
    esac
fi
if [ "$RUN" = false ]; then
    echo "• Run: adb shell am start -n $COMPONENT"
fi
echo "• View logs: ./deploy.sh --logs  (or make logs)"
echo -e "${BLUE}=====================================${NC}"
