plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Everything the tuner knows how to do that is not a user interface: note
 * maths, the tuning catalog, and the pitch pipeline from the high-pass filter
 * through YIN to the smoother.
 *
 * It is a multiplatform module so that the Android app and the web app run the
 * same algorithm rather than two ports of it that drift apart. Nothing here may
 * depend on Android or on the JVM — `jvm()` exists for the Android app to
 * consume and for the recording-based regression tests, not as a licence to
 * reach for `java.*`.
 */
kotlin {
    jvmToolchain(17)

    jvm()

    js(IR) {
        // The browser bundle the web app loads, and Node for running the shared
        // test suite against the JavaScript build of the same sources.
        browser()
        nodejs()
        binaries.library()
        useEsModules()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // The recording fixtures are read off disk and decoded with javax.sound,
        // so that suite can only run on the JVM.
        jvmTest.dependencies {
            implementation(libs.junit)
        }
    }
}

/**
 * Drops the compiled core where the web app expects it.
 *
 * `web/vendor/` is generated, not checked in: it is build output like any APK,
 * and the one thing worse than two copies of an algorithm is two copies where
 * one is a stale compiled artefact nobody remembers regenerating.
 */
val syncWebCore by tasks.registering(Sync::class) {
    group = "build"
    description = "Copies the compiled JavaScript core into web/vendor for the web app."
    from(tasks.named("jsBrowserProductionLibraryDistribution")) {
        // Source maps point at Kotlin files the site does not serve, and the
        // package.json is for npm consumers rather than for a browser.
        exclude("*.map", "package.json")
    }
    into(rootProject.layout.projectDirectory.dir("web/vendor"))
}
