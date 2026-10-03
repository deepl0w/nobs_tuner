package io.github.deeplow.stringtune

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.deeplow.stringtune.ui.StringTuneApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            StringTuneApp(appVersion = versionName())
        }
    }

    /**
     * Read from the package manager rather than BuildConfig so the app does not
     * need a generated BuildConfig class just for one string.
     */
    private fun versionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
    }.getOrDefault("")
}
