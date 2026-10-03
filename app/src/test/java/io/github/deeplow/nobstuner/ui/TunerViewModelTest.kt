package io.github.deeplow.nobstuner.ui

import io.github.deeplow.nobstuner.audio.PitchEstimate
import io.github.deeplow.nobstuner.data.UserSettings
import io.github.deeplow.nobstuner.model.Notes
import io.github.deeplow.nobstuner.model.TuningCatalog
import io.github.deeplow.nobstuner.testing.FakePitchSource
import io.github.deeplow.nobstuner.testing.FakeTunerRepository
import io.github.deeplow.nobstuner.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val standard = TuningCatalog.findById("guitar_standard")!! // E2 A2 D3 G3 B3 E4

    private fun viewModel(
        repository: FakeTunerRepository = FakeTunerRepository(),
        source: FakePitchSource = FakePitchSource(),
    ) = TunerViewModel(repository, source)

    /**
     * The state flows stop when nobody is watching, so a test has to watch.
     * Collecting in the background scope mirrors what the UI does.
     */
    private fun TestScope.observe(viewModel: TunerViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        backgroundScope.launch { viewModel.tunedStrings.collect {} }
    }

    // ---- Listening lifecycle ---------------------------------------------

    @Test
    fun `does not open the microphone until asked to listen`() = runTest(mainDispatcher.dispatcher) {
        val source = FakePitchSource()
        val viewModel = viewModel(source = source)
        observe(viewModel)

        assertEquals(0, source.openCount)
        viewModel.startListening()
        assertEquals(1, source.openCount)
    }

    @Test
    fun `refuses to listen without permission`() = runTest(mainDispatcher.dispatcher) {
        val source = FakePitchSource(permissionGranted = false)
        val viewModel = viewModel(source = source)
        observe(viewModel)

        viewModel.startListening()

        assertEquals(0, source.openCount)
        assertFalse(viewModel.uiState.value.micPermissionGranted)
    }

    @Test
    fun `granting permission starts listening`() = runTest(mainDispatcher.dispatcher) {
        val source = FakePitchSource(permissionGranted = false)
        val viewModel = viewModel(source = source)
        observe(viewModel)

        viewModel.onPermissionResult(granted = true)

        assertTrue(viewModel.uiState.value.micPermissionGranted)
        assertEquals(1, source.openCount)
    }

    @Test
    fun `stopping clears the reading`() = runTest(mainDispatcher.dispatcher) {
        val source = FakePitchSource()
        val viewModel = viewModel(source = source)
        observe(viewModel)

        viewModel.startListening()
        source.emitSteady(Notes.frequencyOf(40))
        assertNotNull(viewModel.uiState.value.reading)

        viewModel.stopListening()
        assertNull(viewModel.uiState.value.reading)
    }

    @Test
    fun `a failing microphone surfaces as an error`() = runTest(mainDispatcher.dispatcher) {
        val source = FakePitchSource(failWith = IllegalStateException("mic busy"))
        val viewModel = viewModel(source = source)
        observe(viewModel)

        viewModel.startListening()

        assertEquals("mic busy", viewModel.uiState.value.audioError)
        assertNull(viewModel.uiState.value.reading)
    }

    @Test
    fun `a missing permission surfaces as a permission error`() = runTest(mainDispatcher.dispatcher) {
        val source = FakePitchSource(failWith = SecurityException("denied"))
        val viewModel = viewModel(source = source)
        observe(viewModel)

        viewModel.startListening()

        assertEquals(
            "Microphone permission is required to tune.",
            viewModel.uiState.value.audioError,
        )
    }

    // ---- Readings --------------------------------------------------------

    @Test
    fun `resolves a played note against the selected tuning`() = runTest(mainDispatcher.dispatcher) {
        val source = FakePitchSource()
        val viewModel = viewModel(source = source)
        observe(viewModel)
        viewModel.startListening()

        source.emitSteady(Notes.frequencyOf(45)) // A2, the fifth string

        val reading = viewModel.uiState.value.reading!!
        assertEquals(1, reading.stringIndex)
        assertEquals(45, reading.targetMidi)
        assertTrue(reading.inTune)
    }

    @Test
    fun `a quiet noisy frame produces no reading`() = runTest(mainDispatcher.dispatcher) {
        val source = FakePitchSource()
        val viewModel = viewModel(source = source)
        observe(viewModel)
        viewModel.startListening()

        repeat(5) { source.emit(PitchEstimate(Notes.frequencyOf(45), clarity = 0.2, levelDbfs = -20.0)) }

        assertNull(viewModel.uiState.value.reading)
    }

    // ---- The autoDetectString setting ------------------------------------

    @Test
    fun `with auto detection off the reading targets the first string`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeTunerRepository(UserSettings(autoDetectString = false))
        val source = FakePitchSource()
        val viewModel = viewModel(repository, source)
        observe(viewModel)
        viewModel.startListening()

        source.emitSteady(Notes.frequencyOf(45)) // playing A2

        val reading = viewModel.uiState.value.reading!!
        assertEquals(0, reading.stringIndex)
        assertEquals(40, reading.targetMidi)
    }

    @Test
    fun `with auto detection off the selector shows the first string as chosen`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeTunerRepository(UserSettings(autoDetectString = false))
        val viewModel = viewModel(repository)
        observe(viewModel)

        assertEquals(0, viewModel.uiState.value.manualStringIndex)
    }

    @Test
    fun `with auto detection on nothing is shown as chosen until tapped`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        observe(viewModel)

        assertNull(viewModel.uiState.value.manualStringIndex)
        viewModel.selectString(3)
        assertEquals(3, viewModel.uiState.value.manualStringIndex)
    }

    // ---- Pinning a string ------------------------------------------------

    @Test
    fun `a pinned string overrides detection`() = runTest(mainDispatcher.dispatcher) {
        val source = FakePitchSource()
        val viewModel = viewModel(source = source)
        observe(viewModel)
        viewModel.startListening()
        viewModel.selectString(0) // pin the low E

        source.emitSteady(Notes.frequencyOf(45)) // but play A2

        assertEquals(0, viewModel.uiState.value.reading!!.stringIndex)
    }

    @Test
    fun `the pin stops applying when the tuning changes`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeTunerRepository()
        val viewModel = viewModel(repository)
        observe(viewModel)

        viewModel.selectString(5)
        assertEquals(5, viewModel.uiState.value.manualStringIndex)

        repository.selectTuning("bass_standard") // 4 strings; index 5 would be invalid

        assertNull(viewModel.uiState.value.manualStringIndex)
    }

    @Test
    fun `the pin stops applying when chromatic mode is entered`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.selectString(2)
        viewModel.setChromaticMode(true)

        assertNull(viewModel.uiState.value.manualStringIndex)
    }

    @Test
    fun `selecting null hands the string back to detection`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.selectString(2)
        viewModel.selectString(null)

        assertNull(viewModel.uiState.value.manualStringIndex)
    }

    // ---- Tuned-string ticks ----------------------------------------------

    @Test
    fun `a string played in tune is remembered`() = runTest(mainDispatcher.dispatcher) {
        val source = FakePitchSource()
        val viewModel = viewModel(source = source)
        observe(viewModel)
        viewModel.startListening()

        source.emitSteady(Notes.frequencyOf(45)) // A2, bang in tune

        assertEquals(setOf(1), viewModel.tunedStrings.value)
    }

    @Test
    fun `a string played out of tune is not remembered`() = runTest(mainDispatcher.dispatcher) {
        val source = FakePitchSource()
        val viewModel = viewModel(source = source)
        observe(viewModel)
        viewModel.startListening()

        source.emitSteady(Notes.frequencyOf(45.3, Notes.DEFAULT_A4_HZ)) // 30 cents sharp

        assertTrue(viewModel.tunedStrings.value.isEmpty())
    }

    @Test
    fun `tuned strings accumulate across notes`() = runTest(mainDispatcher.dispatcher) {
        val source = FakePitchSource()
        val viewModel = viewModel(source = source)
        observe(viewModel)
        viewModel.startListening()

        source.emitSteady(Notes.frequencyOf(45)) // A string
        // Moving to another note takes more than a handful of frames: the
        // smoother eases towards a new pitch rather than jumping to it.
        source.emitSteady(Notes.frequencyOf(50), count = 30) // D string

        assertEquals(setOf(1, 2), viewModel.tunedStrings.value)
    }

    @Test
    fun `tuned strings are cleared when the tuning changes`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeTunerRepository()
        val source = FakePitchSource()
        val viewModel = viewModel(repository, source)
        observe(viewModel)
        viewModel.startListening()

        source.emitSteady(Notes.frequencyOf(45))
        assertEquals(setOf(1), viewModel.tunedStrings.value)

        repository.selectTuning("guitar_drop_d")

        assertTrue(viewModel.tunedStrings.value.isEmpty())
    }

    @Test
    fun `tuned strings are cleared when chromatic mode is toggled`() = runTest(mainDispatcher.dispatcher) {
        val source = FakePitchSource()
        val viewModel = viewModel(source = source)
        observe(viewModel)
        viewModel.startListening()

        source.emitSteady(Notes.frequencyOf(45))
        assertEquals(setOf(1), viewModel.tunedStrings.value)

        viewModel.setChromaticMode(true)

        assertTrue(viewModel.tunedStrings.value.isEmpty())
    }

    // ---- Favourites ------------------------------------------------------

    @Test
    fun `toggling the favourite writes through to the repository`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeTunerRepository()
        val viewModel = viewModel(repository)
        observe(viewModel)

        viewModel.toggleFavorite(standard.id)
        assertEquals(setOf(standard.id), repository.currentFavorites)
        assertTrue(viewModel.uiState.value.isFavorite)

        viewModel.toggleFavorite(standard.id)
        assertFalse(viewModel.uiState.value.isFavorite)
    }

    // ---- Flow lifetime ---------------------------------------------------

    @Test
    fun `nothing is collected when no screen is watching`() = runTest(mainDispatcher.dispatcher) {
        val source = FakePitchSource()
        val viewModel = viewModel(source = source)

        // No observe() here: the view model must not subscribe to itself.
        viewModel.startListening()

        assertEquals(0, source.openCount)
    }
}
