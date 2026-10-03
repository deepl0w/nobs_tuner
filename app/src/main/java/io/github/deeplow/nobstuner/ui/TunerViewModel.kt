package io.github.deeplow.nobstuner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.deeplow.nobstuner.appContainer
import io.github.deeplow.nobstuner.audio.PitchSmoother
import io.github.deeplow.nobstuner.audio.PitchSource
import io.github.deeplow.nobstuner.audio.TrackedPitch
import io.github.deeplow.nobstuner.data.TunerRepository
import io.github.deeplow.nobstuner.data.UserSettings
import io.github.deeplow.nobstuner.model.PitchTargeting
import io.github.deeplow.nobstuner.model.Tuning
import io.github.deeplow.nobstuner.model.TuningCatalog
import io.github.deeplow.nobstuner.model.TuningReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TunerUiState(
    val settings: UserSettings = UserSettings(),
    val tuning: Tuning = TuningCatalog.default,
    val chromaticMode: Boolean = false,
    val reading: TuningReading? = null,
    val isFavorite: Boolean = false,
    /** The string shown as chosen, or null when detection is picking it. */
    val manualStringIndex: Int? = null,
    val micPermissionGranted: Boolean = false,
    val audioError: String? = null,
)

/**
 * Drives the tuner screen: owns the listening session and turns detected
 * pitches into a reading against the selected tuning.
 *
 * Deliberately does not own the tuning library or the settings screen — those
 * belong to [LibraryViewModel] and [SettingsViewModel]. All three read and
 * write the same [TunerRepository], which is what keeps them consistent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModel(
    private val repository: TunerRepository,
    private val pitchSource: PitchSource,
    private val smoother: PitchSmoother = PitchSmoother(),
) : ViewModel() {

    /**
     * What the tuner is aiming at. Chromatic mode and the selected tuning are
     * one concept here because they are alternatives, not independent switches.
     */
    private data class Target(val tuning: Tuning, val chromatic: Boolean) {
        /** Changes whenever the thing being aimed at changes. */
        val key: String get() = if (chromatic) CHROMATIC_KEY else tuning.id
    }

    /**
     * A string the player tapped, remembered against the target it was chosen
     * for. Tying the two together means a pin stops applying by itself when the
     * tuning changes, instead of needing something to watch for that and clear
     * it.
     */
    private data class StringPin(val targetKey: String, val index: Int) {
        fun indexFor(target: Target): Int? = index.takeIf { targetKey == target.key }
    }

    private val listening = MutableStateFlow(false)
    private val pin = MutableStateFlow<StringPin?>(null)
    private val micPermissionGranted = MutableStateFlow(pitchSource.hasPermission())
    private val audioError = MutableStateFlow<String?>(null)

    private val activeTarget: StateFlow<Target> = combine(
        repository.selectedTuningId,
        repository.customTunings,
        repository.chromaticMode,
    ) { id, custom, chromatic ->
        val tuning = TuningCatalog.findById(id)
            ?: custom.firstOrNull { it.id == id }
            ?: TuningCatalog.default
        Target(tuning, chromatic)
    }.stateIn(viewModelScope, WHILE_OBSERVED, Target(TuningCatalog.default, false))

    private val trackedPitch: StateFlow<TrackedPitch?> = listening
        .flatMapLatest { isListening ->
            if (!isListening) {
                smoother.reset()
                flowOf(null)
            } else {
                pitchSource.pitchEstimates()
                    .map { smoother.push(it) }
                    .onStart {
                        smoother.reset()
                        audioError.value = null
                    }
                    .catch { cause ->
                        audioError.value = when (cause) {
                            is SecurityException -> "Microphone permission is required to tune."
                            else -> cause.message ?: "The microphone is unavailable right now."
                        }
                        emit(null)
                    }
            }
        }
        .stateIn(viewModelScope, WHILE_OBSERVED, null)

    private val reading: StateFlow<TuningReading?> = combine(
        trackedPitch,
        repository.settings,
        activeTarget,
        pin,
    ) { pitch, settings, target, pin ->
        pitch?.let { resolve(it, settings, target, pin) }
    }.stateIn(viewModelScope, WHILE_OBSERVED, null)

    /**
     * Strings brought into tune since the target last changed.
     *
     * Accumulated inside the flow graph rather than by a collector in `init`:
     * a permanent internal subscriber would hold every upstream flow open for
     * the view model's whole life and quietly defeat [WHILE_OBSERVED] on all of
     * them. Restarting the fold on each target change is also what clears the
     * ticks, so no separate reset is needed.
     */
    val tunedStrings: StateFlow<Set<Int>> = activeTarget
        .map { it.key }
        .distinctUntilChanged()
        .flatMapLatest { accumulateTunedStrings() }
        .stateIn(viewModelScope, WHILE_OBSERVED, emptySet())

    val uiState: StateFlow<TunerUiState> = combine(
        repository.settings,
        activeTarget,
        reading,
        pin,
        session(),
    ) { settings, target, reading, pin, session ->
        TunerUiState(
            settings = settings,
            tuning = target.tuning,
            chromaticMode = target.chromatic,
            reading = reading,
            isFavorite = target.tuning.id in session.favorites,
            manualStringIndex = PitchTargeting.effectivePinnedIndex(
                manualIndex = pin?.indexFor(target),
                autoDetect = settings.autoDetectString,
                chromatic = target.chromatic,
                tuning = target.tuning,
            ),
            micPermissionGranted = session.permission,
            audioError = session.error,
        )
    }.stateIn(viewModelScope, WHILE_OBSERVED, TunerUiState())

    // ---- Audio lifecycle -------------------------------------------------

    fun startListening() {
        micPermissionGranted.value = pitchSource.hasPermission()
        listening.value = micPermissionGranted.value
    }

    fun stopListening() {
        listening.value = false
    }

    fun onPermissionResult(granted: Boolean) {
        micPermissionGranted.value = granted
        if (granted) {
            audioError.value = null
            listening.value = true
        } else {
            listening.value = false
        }
    }

    // ---- User actions ----------------------------------------------------

    /** Pass null to stop aiming at one string by hand. */
    fun selectString(index: Int?) {
        pin.value = index?.let { StringPin(activeTarget.value.key, it) }
    }

    fun setChromaticMode(enabled: Boolean) =
        viewModelScope.launch { repository.setChromaticMode(enabled) }

    fun toggleFavorite(id: String) = viewModelScope.launch { repository.toggleFavorite(id) }

    // ---- Internals -------------------------------------------------------

    private data class SessionState(
        val permission: Boolean,
        val error: String?,
        val favorites: Set<String>,
    )

    private fun session(): Flow<SessionState> = combine(
        micPermissionGranted,
        audioError,
        repository.favoriteIds,
    ) { permission, error, favorites -> SessionState(permission, error, favorites) }

    /**
     * Folds readings into the set of strings already brought into tune.
     *
     * [reading] is a state flow, so a fresh collector is handed the reading
     * that was current *before* the target changed. Dropping it is what makes
     * the restart actually clear the ticks instead of immediately re-earning
     * one from a note that is still ringing.
     */
    private fun accumulateTunedStrings(): Flow<Set<Int>> =
        reading.drop(1).scan(emptySet<Int>()) { tuned, reading ->
            val index = reading?.stringIndex
            if (reading != null && reading.inTune && index != null) tuned + index else tuned
        }

    private fun resolve(
        pitch: TrackedPitch,
        settings: UserSettings,
        target: Target,
        pin: StringPin?,
    ): TuningReading = if (target.chromatic) {
        PitchTargeting.resolveChromatic(
            frequencyHz = pitch.frequencyHz,
            clarity = pitch.clarity,
            levelDbfs = pitch.levelDbfs,
            a4Hz = settings.referencePitchHz,
            toleranceCents = settings.toleranceCents,
        )
    } else {
        PitchTargeting.resolveAgainstTuning(
            frequencyHz = pitch.frequencyHz,
            clarity = pitch.clarity,
            levelDbfs = pitch.levelDbfs,
            tuning = target.tuning,
            a4Hz = settings.referencePitchHz,
            toleranceCents = settings.toleranceCents,
            manualIndex = pin?.indexFor(target),
            autoDetect = settings.autoDetectString,
        )
    }

    companion object {
        private const val CHROMATIC_KEY = "\u0000chromatic"

        /**
         * Keeps the graph alive briefly across a configuration change, then lets
         * it go. Nothing inside this class subscribes, so this means what it
         * says: no UI, no work.
         */
        private val WHILE_OBSERVED = SharingStarted.WhileSubscribed(5_000)

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                TunerViewModel(container.repository, container.pitchSource)
            }
        }
    }
}
