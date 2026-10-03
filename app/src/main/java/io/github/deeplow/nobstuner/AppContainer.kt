package io.github.deeplow.nobstuner

import android.content.Context
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.deeplow.nobstuner.audio.AudioEngine
import io.github.deeplow.nobstuner.audio.PitchSource
import io.github.deeplow.nobstuner.data.DataStoreTunerRepository
import io.github.deeplow.nobstuner.data.TunerRepository

/**
 * The app's composition root: the one place that decides which concrete
 * implementations the view models get.
 *
 * Hand-written rather than generated. The graph is two objects deep and does
 * not branch, so a dependency-injection framework would cost more to read than
 * it saves. What matters is that construction happens *here* and not inside the
 * view models, which is what lets the tests substitute fakes.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Both are stateless and safe to share; the mic is opened per collection. */
    val repository: TunerRepository by lazy { DataStoreTunerRepository(appContext) }

    val pitchSource: PitchSource by lazy { AudioEngine(appContext) }
}

/** Reaches the container from inside a `viewModelFactory { initializer { ... } }`. */
internal fun CreationExtras.appContainer(): AppContainer {
    val application = checkNotNull(this[APPLICATION_KEY]) { "No Application in CreationExtras" }
    return (application as NobsTunerApplication).container
}
