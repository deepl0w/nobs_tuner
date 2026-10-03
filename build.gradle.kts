import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Kotlin/JS would otherwise fetch its own Node and Yarn from repositories it
// adds to the project itself, which settings.gradle.kts refuses on principle —
// every dependency this build resolves is declared there. Use whatever Node and
// Yarn are on the PATH instead; `./test.sh --check` says so when they are not.
allprojects {
    plugins.withType<NodeJsPlugin> {
        the<NodeJsEnvSpec>().download.set(false)
    }
    plugins.withType<YarnPlugin> {
        the<YarnRootEnvSpec>().download.set(false)
    }
}
