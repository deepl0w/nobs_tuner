#!/usr/bin/env bash

# StringTune - Test Script
# Runs the test suites, and can check the toolchain before you start

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

cd "$(dirname "${BASH_SOURCE[0]}")"

APP_ID=$(grep -oP 'applicationId\s*=\s*"\K[^"]+' app/build.gradle.kts)
AUDIO_DIR="app/src/test/resources/realaudio"

echo -e "${BLUE}=====================================${NC}"
echo -e "${BLUE}StringTune - Test Script${NC}"
echo -e "${BLUE}=====================================${NC}"
echo ""

# Parse arguments
RUN_UNIT=false
RUN_LINT=false
RUN_DEVICE=false
FETCH_AUDIO=false
CHECK_ONLY=false
SELECTED=false
SERIAL=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --unit|-u)      RUN_UNIT=true;   SELECTED=true; shift ;;
        --lint|-l)      RUN_LINT=true;   SELECTED=true; shift ;;
        --device|-d)    RUN_DEVICE=true; SELECTED=true; shift ;;
        --audio|-a)     FETCH_AUDIO=true; RUN_UNIT=true; SELECTED=true; shift ;;
        --all)          RUN_UNIT=true; RUN_LINT=true; RUN_DEVICE=true; SELECTED=true; shift ;;
        --check|-c)     CHECK_ONLY=true; SELECTED=true; shift ;;
        --serial|-s)    SERIAL="$2"; shift 2 ;;
        --help|-h)
            echo "Usage: ./test.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --unit, -u       JVM unit tests (default)"
            echo "  --audio, -a      Download instrument recordings, then run unit tests"
            echo "  --lint, -l       Android lint on the release variant"
            echo "  --device, -d     Instrumented tests on a connected device"
            echo "  --all            Unit tests, lint and instrumented tests"
            echo "  --check, -c      Check the toolchain without running tests"
            echo "  --serial, -s     Device serial for --device (see adb devices)"
            echo "  --help, -h       Show this help message"
            echo ""
            echo "Examples:"
            echo "  ./test.sh                 # Unit tests"
            echo "  ./test.sh --audio         # Unit tests including real recordings"
            echo "  ./test.sh --all           # Everything, needs a device attached"
            echo "  ./test.sh --check         # Is this machine set up to build?"
            echo ""
            echo "The real-recording tests skip unless the fixtures are present."
            echo "Fetch them once with --audio, or tools/fetch-test-audio.sh."
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Run './test.sh --help' for usage information"
            exit 1
            ;;
    esac
done

# Default to the unit tests.
if [ "$SELECTED" = false ]; then
    RUN_UNIT=true
fi

# ---- Toolchain check -----------------------------------------------------

if [ "$CHECK_ONLY" = true ]; then
    ERRORS=0

    echo -n "Checking Java... "
    if command -v java >/dev/null 2>&1; then
        JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
        if [ "$JAVA_VERSION" -ge 17 ]; then
            echo -e "${GREEN}✓ Java $JAVA_VERSION${NC}"
        else
            echo -e "${RED}✗ Java 17+ required (found $JAVA_VERSION)${NC}"
            ERRORS=$((ERRORS + 1))
        fi
    else
        echo -e "${RED}✗ Not found${NC}"
        ERRORS=$((ERRORS + 1))
    fi

    echo -n "Checking Android SDK... "
    SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if [ -z "$SDK_DIR" ] && [ -f local.properties ]; then
        SDK_DIR=$(grep -oP '^sdk\.dir=\K.*' local.properties || true)
    fi
    if [ -n "$SDK_DIR" ] && [ -d "$SDK_DIR" ]; then
        echo -e "${GREEN}✓ $SDK_DIR${NC}"
    else
        echo -e "${RED}✗ Not found${NC}"
        echo "    Set ANDROID_HOME, or put sdk.dir=... in local.properties"
        ERRORS=$((ERRORS + 1))
    fi

    echo -n "Checking Gradle wrapper... "
    if [ -x ./gradlew ]; then
        echo -e "${GREEN}✓ Found${NC}"
    else
        echo -e "${RED}✗ Missing or not executable${NC}"
        ERRORS=$((ERRORS + 1))
    fi

    echo -n "Checking ADB... "
    if command -v adb >/dev/null 2>&1; then
        echo -e "${GREEN}✓ Found${NC}"
    else
        echo -e "${YELLOW}⚠ Not in PATH (only needed for --device)${NC}"
    fi

    echo -n "Checking test recordings... "
    COUNT=$(ls "$AUDIO_DIR"/*.wav 2>/dev/null | wc -l)
    if [ "$COUNT" -gt 0 ]; then
        echo -e "${GREEN}✓ $COUNT present${NC}"
    else
        echo -e "${YELLOW}⚠ Absent — those tests will skip (./test.sh --audio)${NC}"
    fi

    echo -n "Testing Gradle configuration... "
    if ./gradlew :app:tasks >/dev/null 2>&1; then
        echo -e "${GREEN}✓ Success${NC}"
    else
        echo -e "${RED}✗ Failed${NC}"
        ERRORS=$((ERRORS + 1))
    fi

    echo ""
    if [ "$ERRORS" -eq 0 ]; then
        echo -e "${GREEN}=====================================${NC}"
        echo -e "${GREEN}Environment looks good${NC}"
        echo -e "${GREEN}=====================================${NC}"
        exit 0
    else
        echo -e "${RED}=====================================${NC}"
        echo -e "${RED}$ERRORS problem(s) found${NC}"
        echo -e "${RED}=====================================${NC}"
        exit 1
    fi
fi

# ---- Test recordings -----------------------------------------------------

if [ "$FETCH_AUDIO" = true ]; then
    echo -e "${YELLOW}Fetching instrument recordings...${NC}"
    tools/fetch-test-audio.sh
    echo ""
fi

FAILURES=0

# ---- Unit tests ----------------------------------------------------------

if [ "$RUN_UNIT" = true ]; then
    echo -e "${YELLOW}Running JVM unit tests...${NC}"
    AUDIO_COUNT=$(ls "$AUDIO_DIR"/*.wav 2>/dev/null | wc -l)
    if [ "$AUDIO_COUNT" -eq 0 ]; then
        echo -e "${YELLOW}⚠${NC} No instrument recordings — those tests will skip."
        echo "  Run ./test.sh --audio to include them."
    fi
    if ./gradlew :app:testDebugUnitTest; then
        echo -e "${GREEN}✓${NC} Unit tests passed"
    else
        echo -e "${RED}✗${NC} Unit tests failed"
        echo "  Report: app/build/reports/tests/testDebugUnitTest/index.html"
        FAILURES=$((FAILURES + 1))
    fi
    echo ""
fi

# ---- Lint ----------------------------------------------------------------

if [ "$RUN_LINT" = true ]; then
    echo -e "${YELLOW}Running lint (release variant)...${NC}"
    if ./gradlew :app:lintRelease; then
        echo -e "${GREEN}✓${NC} Lint clean"
    else
        echo -e "${RED}✗${NC} Lint found problems"
        echo "  Report: app/build/reports/lint-results-release.html"
        FAILURES=$((FAILURES + 1))
    fi
    echo ""
fi

# ---- Instrumented tests --------------------------------------------------

if [ "$RUN_DEVICE" = true ]; then
    echo -e "${YELLOW}Running instrumented tests...${NC}"
    DEVICE_LIST=$(adb devices 2>/dev/null | grep -v "List" | grep "device$" || true)
    DEVICE_COUNT=$(echo "$DEVICE_LIST" | grep -c "device" || true)

    if [ "$DEVICE_COUNT" -eq 0 ]; then
        echo -e "${RED}✗${NC} No device found — connect one or start an emulator"
        FAILURES=$((FAILURES + 1))
    elif [ "$DEVICE_COUNT" -gt 1 ] && [ -z "$SERIAL" ]; then
        # Gradle would otherwise install and run on every attached device,
        # which is rarely what you want when a personal phone is plugged in.
        echo -e "${RED}✗${NC} More than one device is connected:"
        echo "$DEVICE_LIST" | sed 's/^/    /'
        echo ""
        echo "  Name the one to test with --serial, e.g.:"
        echo "    ./test.sh --device --serial $(echo "$DEVICE_LIST" | head -1 | awk '{print $1}')"
        FAILURES=$((FAILURES + 1))
    else
        if [ -n "$SERIAL" ]; then
            if ! echo "$DEVICE_LIST" | awk '{print $1}' | grep -qx "$SERIAL"; then
                echo -e "${RED}✗${NC} Device '$SERIAL' is not connected"
                FAILURES=$((FAILURES + 1))
                SERIAL=""
            else
                echo "Target: $SERIAL"
                # AGP honours ANDROID_SERIAL when picking devices.
                export ANDROID_SERIAL="$SERIAL"
            fi
        fi
        if [ "$FAILURES" -eq 0 ] || [ -n "$ANDROID_SERIAL" ]; then
            if ./gradlew :app:connectedDebugAndroidTest; then
                echo -e "${GREEN}✓${NC} Instrumented tests passed"
            else
                echo -e "${RED}✗${NC} Instrumented tests failed"
                echo "  Report: app/build/reports/androidTests/connected/debug/index.html"
                FAILURES=$((FAILURES + 1))
            fi
        fi
    fi
    echo ""
fi

# ---- Summary -------------------------------------------------------------

if [ "$FAILURES" -eq 0 ]; then
    echo -e "${GREEN}=====================================${NC}"
    echo -e "${GREEN}All selected checks passed${NC}"
    echo -e "${GREEN}=====================================${NC}"
else
    echo -e "${RED}=====================================${NC}"
    echo -e "${RED}$FAILURES check(s) failed${NC}"
    echo -e "${RED}=====================================${NC}"
    exit 1
fi
