# Nobs Tuner Makefile
# Provides simple commands for building, testing and deploying without Android Studio

.PHONY: help check test test-audio lint verify build install run deploy release \
        release-install bundle clean gradle-clean logs devices uninstall \
        restart-adb launch stop grant-permissions info device-test dev quick full

# Default target
.DEFAULT_GOAL := help

# Colors. Built with printf so they survive a plain `echo` under /bin/sh,
# which does not expand backslash escapes.
GREEN  := $(shell printf '\033[0;32m')
YELLOW := $(shell printf '\033[1;33m')
BLUE   := $(shell printf '\033[0;34m')
NC     := $(shell printf '\033[0m')

# Read the ids from Gradle so a rename before publishing does not break these.
# sed rather than `grep -oP`, which only exists in GNU grep: under BSD userland
# the ids would come back empty and the adb targets would run malformed.
gradle_value = $(shell sed -n 's/.*$(1)[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)

APP_ID        := $(call gradle_value,applicationId)
NAMESPACE     := $(call gradle_value,namespace)
DEBUG_SUFFIX  := $(call gradle_value,applicationIdSuffix)
DEBUG_PACKAGE := $(APP_ID)$(DEBUG_SUFFIX)

ifeq ($(strip $(APP_ID)),)
$(error Could not read applicationId from app/build.gradle.kts)
endif
ifeq ($(strip $(NAMESPACE)),)
$(error Could not read namespace from app/build.gradle.kts)
endif

help: ## Show this help message
	@echo "$(BLUE)Nobs Tuner Build Commands$(NC)"
	@echo "======================================"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "$(GREEN)%-18s$(NC) %s\n", $$1, $$2}'
	@echo ""
	@echo "$(YELLOW)Examples:$(NC)"
	@echo "  make test         # Run the unit tests"
	@echo "  make run          # Build, install, and launch app"
	@echo "  make bundle       # Build the AAB for the Play Store"

check: ## Check the toolchain is set up
	@./test.sh --check

test: ## Run JVM unit tests
	@./test.sh --unit

test-audio: ## Fetch instrument recordings, then run unit tests
	@./test.sh --audio

device-test: ## Run instrumented tests on a connected device
	@./test.sh --device

test-web: ## Run the shared pitch suite against the JavaScript build (needs Node)
	@./gradlew :core:jsNodeTest

web: ## Compile the core and serve the web app at http://localhost:8000
	@./gradlew :core:syncWebCore
	@echo "Serving web/ at http://localhost:8000 — Ctrl-C to stop."
	@python3 -m http.server 8000 --directory web

lint: ## Run Android lint on the release variant
	@./test.sh --lint

verify: ## Unit tests, lint and instrumented tests
	@./test.sh --all

build: ## Build debug APK
	@./build.sh

install: ## Build and install debug on device
	@./build.sh --install

run: ## Build, install and launch app
	@./build.sh --run

deploy: ## Deploy existing APK to device and follow logs
	@./deploy.sh --launch

release: ## Build release APK
	@./build.sh --release

release-install: ## Build and install release
	@./build.sh --release --install

bundle: ## Build the Play Store App Bundle (.aab)
	@./build.sh --bundle

clean: ## Clean build artifacts
	@echo "$(BLUE)Cleaning build artifacts...$(NC)"
	@./gradlew clean
	@rm -rf build
	@rm -rf */build
	@echo "$(GREEN)✓ Clean complete$(NC)"

gradle-clean: ## Deep clean Gradle caches
	@echo "$(BLUE)Deep cleaning Gradle...$(NC)"
	@./gradlew clean || true
	@rm -rf .gradle .kotlin build */build
	@echo "$(GREEN)✓ Deep clean complete$(NC)"

logs: ## Follow the app's logs from the device
	@./deploy.sh --logs

devices: ## List connected devices
	@echo "$(BLUE)Connected devices:$(NC)"
	@adb devices -l

uninstall: ## Uninstall app from device
	@echo "$(BLUE)Uninstalling app...$(NC)"
	@adb uninstall $(DEBUG_PACKAGE) || true
	@adb uninstall $(APP_ID) || true
	@echo "$(GREEN)✓ Uninstalled$(NC)"

restart-adb: ## Restart ADB server
	@echo "$(BLUE)Restarting ADB...$(NC)"
	@adb kill-server
	@adb start-server
	@echo "$(GREEN)✓ ADB restarted$(NC)"

launch: ## Launch app on device
	@echo "$(BLUE)Launching app...$(NC)"
	@adb shell am start -n $(DEBUG_PACKAGE)/$(NAMESPACE).MainActivity

stop: ## Stop app on device
	@echo "$(BLUE)Stopping app...$(NC)"
	@adb shell am force-stop $(DEBUG_PACKAGE)

grant-permissions: ## Grant app permissions
	@echo "$(BLUE)Granting permissions...$(NC)"
	@adb shell pm grant $(DEBUG_PACKAGE) android.permission.RECORD_AUDIO || true
	@echo "$(GREEN)✓ Permissions granted$(NC)"

info: ## Show build info
	@echo "$(BLUE)Build Information$(NC)"
	@echo "======================================"
	@echo "Java Version:"
	@java -version 2>&1 | head -n 1
	@echo ""
	@echo "Android SDK:"
	@echo "  ANDROID_HOME = $(ANDROID_HOME)"
	@echo ""
	@echo "Gradle:"
	@./gradlew --version | grep "Gradle"
	@echo ""
	@echo "Packages:"
	@echo "  Debug:   $(DEBUG_PACKAGE)"
	@echo "  Release: $(APP_ID)"
	@echo ""
	@echo "Artifact Locations:"
	@echo "  Debug:   app/build/outputs/apk/debug/"
	@echo "  Release: app/build/outputs/apk/release/"
	@echo "  Bundle:  app/build/outputs/bundle/release/"

# Development shortcuts
dev: clean build install ## Clean, build, and install (dev cycle)

quick: build install ## Quick rebuild and install

full: clean test build install launch logs ## Full dev cycle with logs
