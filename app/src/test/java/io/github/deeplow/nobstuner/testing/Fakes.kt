package io.github.deeplow.nobstuner.testing

import io.github.deeplow.nobstuner.audio.PitchEstimate
import io.github.deeplow.nobstuner.audio.PitchSource
import io.github.deeplow.nobstuner.data.DisplayStyle
import io.github.deeplow.nobstuner.data.ThemeMode
import io.github.deeplow.nobstuner.data.TunerRepository
import io.github.deeplow.nobstuner.data.UserSettings
import io.github.deeplow.nobstuner.model.Tuning
import io.github.deeplow.nobstuner.model.TuningCatalog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps `Dispatchers.Main` for a test dispatcher so `viewModelScope` runs inline.
 *
 * Tests must pass [dispatcher] to `runTest` so that the view model's scope and
 * the test's own `backgroundScope` share one scheduler; without that the
 * collectors that keep the state flows alive never get to run.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

/**
 * In-memory [TunerRepository]. Writes land in the same flows the view models
 * read, so a test can assert on what a user action actually persisted.
 */
class FakeTunerRepository(
    initialSettings: UserSettings = UserSettings(),
    initialCustomTunings: List<Tuning> = emptyList(),
    initialFavorites: Set<String> = emptySet(),
    initialSelectedId: String = TuningCatalog.default.id,
    initialChromatic: Boolean = false,
) : TunerRepository {

    private val settingsState = MutableStateFlow(initialSettings)
    private val customState = MutableStateFlow(initialCustomTunings)
    private val favoritesState = MutableStateFlow(initialFavorites)
    private val selectedState = MutableStateFlow(initialSelectedId)
    private val chromaticState = MutableStateFlow(initialChromatic)

    override val settings: Flow<UserSettings> = settingsState
    override val customTunings: Flow<List<Tuning>> = customState
    override val favoriteIds: Flow<Set<String>> = favoritesState
    override val selectedTuningId: Flow<String> = selectedState
    override val chromaticMode: Flow<Boolean> = chromaticState

    /** Direct reads, for asserting without collecting. */
    val currentSettings: UserSettings get() = settingsState.value
    val currentFavorites: Set<String> get() = favoritesState.value
    val currentCustomTunings: List<Tuning> get() = customState.value
    val currentSelectedId: String get() = selectedState.value
    val currentChromatic: Boolean get() = chromaticState.value

    override suspend fun setReferencePitch(hz: Double) {
        settingsState.value = settingsState.value.copy(
            referencePitchHz = UserSettings.clampReferencePitch(hz),
        )
    }

    override suspend fun setUseFlats(value: Boolean) {
        settingsState.value = settingsState.value.copy(useFlats = value)
    }

    override suspend fun setAutoDetectString(value: Boolean) {
        settingsState.value = settingsState.value.copy(autoDetectString = value)
    }

    override suspend fun setKeepScreenOn(value: Boolean) {
        settingsState.value = settingsState.value.copy(keepScreenOn = value)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        settingsState.value = settingsState.value.copy(themeMode = mode)
    }

    override suspend fun setToleranceCents(cents: Int) {
        settingsState.value = settingsState.value.copy(
            toleranceCents = UserSettings.clampToleranceCents(cents),
        )
    }

    override suspend fun setDisplayStyle(style: DisplayStyle) {
        settingsState.value = settingsState.value.copy(displayStyle = style)
    }

    override suspend fun setChromaticMode(value: Boolean) {
        chromaticState.value = value
    }

    override suspend fun selectTuning(id: String) {
        selectedState.value = id
        chromaticState.value = false
    }

    override suspend fun toggleFavorite(id: String) {
        val current = favoritesState.value
        favoritesState.value = if (id in current) current - id else current + id
    }

    override suspend fun saveCustomTuning(tuning: Tuning) {
        val normalised = tuning.copy(isCustom = true)
        customState.value = customState.value.filterNot { it.id == normalised.id } + normalised
    }

    override suspend fun deleteCustomTuning(id: String) {
        customState.value = customState.value.filterNot { it.id == id }
        favoritesState.value = favoritesState.value - id
        if (selectedState.value == id) selectedState.value = TuningCatalog.default.id
    }
}

/**
 * A [PitchSource] fed by the test rather than by a microphone.
 *
 * This is the seam that makes the listening state machine testable at all: an
 * emulator cannot be handed host audio, so without it none of these paths could
 * be exercised off-device.
 */
class FakePitchSource(
    var permissionGranted: Boolean = true,
    /** When set, collecting the flow fails with this instead of emitting. */
    var failWith: Throwable? = null,
) : PitchSource {

    private val frames = MutableSharedFlow<PitchEstimate>(extraBufferCapacity = 64)

    /** How many times a collector has opened the source. */
    var openCount: Int = 0
        private set

    override fun hasPermission(): Boolean = permissionGranted

    override fun pitchEstimates(): Flow<PitchEstimate> = flow {
        openCount++
        failWith?.let { throw it }
        frames.collect { emit(it) }
    }

    /** Pushes one analysed frame to whoever is collecting. */
    suspend fun emit(estimate: PitchEstimate) {
        frames.emit(estimate)
    }

    /**
     * Pushes [count] identical frames. The smoother will not report a pitch
     * until a few frames agree, so a single frame is never enough.
     */
    suspend fun emitSteady(hz: Double, count: Int = 5, clarity: Double = 0.95, levelDbfs: Double = -20.0) {
        repeat(count) { emit(PitchEstimate(hz, clarity, levelDbfs)) }
    }
}
