package io.github.deeplow.nobstuner

import android.app.Application

/** Owns the [AppContainer] for the life of the process. */
class NobsTunerApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
