package io.github.deeplow.nobstuner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.deeplow.nobstuner.audio.AudioEngine
import io.github.deeplow.nobstuner.audio.PitchSmoother
import io.github.deeplow.nobstuner.audio.TrackedPitch
import io.github.deeplow.nobstuner.data.DisplayStyle
import io.github.deeplow.nobstuner.data.ThemeMode
import io.github.deeplow.nobstuner.data.TunerRepository
import io.github.deeplow.nobstuner.data.UserSettings
import io.github.deeplow.nobstuner.model.InstrumentFamily
import io.github.deeplow.nobstuner.model.PitchTargeting
import io.github.deeplow.nobstuner.model.Tuning
import io.github.deeplow.nobstuner.model.TuningCatalog
import io.github.deeplow.nobstuner.model.TuningReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class TunerUiState(
    val settings: UserSettings = UserSettings(),
    val tuning: Tuning = TuningCatalog.default,
    val chromaticMode: Boolean = false,
    val reading: TuningReading? = null,
    val isFavorite: Boolean = false,
    /** Null means "auto": whichever string is being played. */
    val manualStringIndex: Int? = null,
    val micPermissionGranted: Boolean = false,
    val audioError: String? = null,
)

data class LibraryState(
    val favorites: List<Tuning> = emptyList(),
    val presetsByFamily: Map<InstrumentFamily, List<Tuning>> = emptyMap(),
    val customTunings: List<Tuning> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val selectedId: String = TuningCatalog.default.id,
    val useFlats: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TunerRepository(application)
    private val audioEngine = AudioEngine(application)
    private val smoother = PitchSmoother()

    private val listening = MutableStateFlow(false)
    private val manualStringIndex = MutableStateFlow<Int?>(null)
    private val micPermissionGranted = MutableStateFlow(audioEngine.hasPermission())
    private val audioError = MutableStateFlow<String?>(null)

    /** Strings the user has already brought into tune this session. */
    private val _tunedStrings = MutableStateFlow<Set<Int>>(emptySet())
    val tunedStrings: StateFlow<Set<Int>> = _tunedStrings.asStateFlow()

    private val trackedPitch: StateFlow<TrackedPitch?> = listening
        .flatMapLatest { isListening ->
            if (!isListening) {
                smoother.reset()
                flowOf(null)
            } else {
                audioEngine.pitchEstimates()
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The tuning currently selected, resolved across presets and custom entries. */
    private val activeTuning: StateFlow<Tuning> =
        combine(repository.selectedTuningId, repository.customTunings) { id, custom ->
            TuningCatalog.findById(id)
                ?: custom.firstOrNull { it.id == id }
                ?: TuningCatalog.default
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TuningCatalog.default)

    private val session = combine(
        repository.chromaticMode,
        micPermissionGranted,
        audioError,
        repository.favoriteIds,
    ) { chromatic, permission, error, favorites ->
        SessionState(chromatic, permission, error, favorites)
    }

    val uiState: StateFlow<TunerUiState> = combine(
        repository.settings,
        activeTuning,
        trackedPitch,
        manualStringIndex,
        session,
    ) { settings, tuning, pitch, manual, session ->
        TunerUiState(
            settings = settings,
            tuning = tuning,
            chromaticMode = session.chromatic,
            reading = pitch?.let { resolve(it, settings, tuning, session.chromatic, manual) },
            isFavorite = tuning.id in session.favorites,
            manualStringIndex = manual,
            micPermissionGranted = session.permission,
            audioError = session.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TunerUiState())

    val libraryState: StateFlow<LibraryState> = combine(
        repository.customTunings,
        repository.favoriteIds,
        repository.selectedTuningId,
        repository.settings,
    ) { custom, favoriteIds, selectedId, settings ->
        val everything = TuningCatalog.presets + custom
        LibraryState(
            favorites = everything.filter { it.id in favoriteIds },
            presetsByFamily = TuningCatalog.presets.groupBy { it.family },
            customTunings = custom,
            favoriteIds = favoriteIds,
            selectedId = selectedId,
            useFlats = settings.useFlats,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    init {
        // Clearing the "already tuned" ticks belongs with whatever changed the
        // target, so it happens here rather than in each call site.
        viewModelScope.launch {
            combine(activeTuning, repository.chromaticMode) { tuning, chromatic ->
                tuning.id to chromatic
            }.distinctUntilChanged().collect {
                _tunedStrings.value = emptySet()
                manualStringIndex.value = null
            }
        }
        viewModelScope.launch {
            uiState.collect { state ->
                val reading = state.reading ?: return@collect
                val index = reading.stringIndex ?: return@collect
                if (reading.inTune) {
                    _tunedStrings.value = _tunedStrings.value + index
                }
            }
        }
    }

    // ---- Audio lifecycle -------------------------------------------------

    fun startListening() {
        micPermissionGranted.value = audioEngine.hasPermission()
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

    fun selectTuning(id: String) = viewModelScope.launch { repository.selectTuning(id) }

    fun setChromaticMode(enabled: Boolean) =
        viewModelScope.launch { repository.setChromaticMode(enabled) }

    fun toggleFavorite(id: String) = viewModelScope.launch { repository.toggleFavorite(id) }

    /** Pass null to go back to automatic string detection. */
    fun selectString(index: Int?) {
        manualStringIndex.value = index
    }

    fun clearTunedStrings() {
        _tunedStrings.value = emptySet()
    }

    fun setReferencePitch(hz: Double) = viewModelScope.launch { repository.setReferencePitch(hz) }

    fun setUseFlats(value: Boolean) = viewModelScope.launch { repository.setUseFlats(value) }

    fun setAutoDetectString(value: Boolean) = viewModelScope.launch {
        repository.setAutoDetectString(value)
        // Going manual needs a string to sit on; going automatic releases it.
        manualStringIndex.value = if (value) null else manualStringIndex.value ?: 0
    }

    fun setKeepScreenOn(value: Boolean) = viewModelScope.launch { repository.setKeepScreenOn(value) }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repository.setThemeMode(mode) }

    fun setToleranceCents(cents: Int) = viewModelScope.launch { repository.setToleranceCents(cents) }

    fun setDisplayStyle(style: DisplayStyle) =
        viewModelScope.launch { repository.setDisplayStyle(style) }

    // ---- Custom tunings --------------------------------------------------

    /** Saves a new tuning when [existingId] is null, otherwise overwrites it. */
    fun saveCustomTuning(
        existingId: String?,
        name: String,
        family: InstrumentFamily,
        strings: List<Int>,
        selectAfterSave: Boolean = true,
    ) = viewModelScope.launch {
        val tuning = Tuning(
            id = existingId ?: "custom_${UUID.randomUUID()}",
            name = name.trim().ifBlank { "Untitled tuning" },
            family = family,
            strings = strings,
            isCustom = true,
        )
        repository.saveCustomTuning(tuning)
        if (selectAfterSave) repository.selectTuning(tuning.id)
    }

    fun deleteCustomTuning(id: String) =
        viewModelScope.launch { repository.deleteCustomTuning(id) }

    fun findTuning(id: String): Tuning? =
        TuningCatalog.findById(id) ?: libraryState.value.customTunings.firstOrNull { it.id == id }

    // ---- Internals -------------------------------------------------------

    private data class SessionState(
        val chromatic: Boolean,
        val permission: Boolean,
        val error: String?,
        val favorites: Set<String>,
    )

    private fun resolve(
        pitch: TrackedPitch,
        settings: UserSettings,
        tuning: Tuning,
        chromatic: Boolean,
        manual: Int?,
    ): TuningReading = if (chromatic) {
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
            tuning = tuning,
            a4Hz = settings.referencePitchHz,
            toleranceCents = settings.toleranceCents,
            manualIndex = manual,
            autoDetect = settings.autoDetectString,
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TunerViewModel(checkNotNull(this[APPLICATION_KEY]))
            }
        }
    }
}
