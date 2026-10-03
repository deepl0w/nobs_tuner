package io.github.deeplow.nobstuner.ui

import io.github.deeplow.nobstuner.data.DisplayStyle
import io.github.deeplow.nobstuner.data.ThemeMode
import io.github.deeplow.nobstuner.data.UserSettings
import io.github.deeplow.nobstuner.testing.FakeTunerRepository
import io.github.deeplow.nobstuner.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun viewModel(repository: FakeTunerRepository = FakeTunerRepository()) =
        SettingsViewModel(repository)

    private fun TestScope.observe(viewModel: SettingsViewModel) {
        backgroundScope.launch { viewModel.settings.collect {} }
    }

    @Test
    fun `every setting writes through and is read back`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeTunerRepository()
        val viewModel = viewModel(repository)
        observe(viewModel)

        viewModel.setReferencePitch(442.0)
        viewModel.setUseFlats(true)
        viewModel.setAutoDetectString(false)
        viewModel.setKeepScreenOn(false)
        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setToleranceCents(3)
        viewModel.setDisplayStyle(DisplayStyle.entries.last())

        val settings = viewModel.settings.value
        assertEquals(442.0, settings.referencePitchHz, 1e-9)
        assertTrue(settings.useFlats)
        assertEquals(false, settings.autoDetectString)
        assertEquals(false, settings.keepScreenOn)
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(3, settings.toleranceCents)
        assertEquals(DisplayStyle.entries.last(), settings.displayStyle)
    }

    @Test
    fun `the settings flow starts from what is already stored`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeTunerRepository(UserSettings(referencePitchHz = 415.0, toleranceCents = 9))
        val viewModel = viewModel(repository)
        observe(viewModel)

        assertEquals(415.0, viewModel.settings.value.referencePitchHz, 1e-9)
        assertEquals(9, viewModel.settings.value.toleranceCents)
    }

    /**
     * The reference pitch and the tolerance are clamped on the way in, so a
     * slider that overshoots cannot persist a value the rest of the app would
     * have to defend against. The real repository does this; a fake that does
     * not is a fake that lets a bad value through in every test written against
     * it.
     */
    @Test
    fun `a reference pitch outside the supported range is clamped`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeTunerRepository()
        val viewModel = viewModel(repository)
        observe(viewModel)

        viewModel.setReferencePitch(1000.0)
        assertEquals(
            UserSettings.REFERENCE_PITCH_RANGE.endInclusive,
            viewModel.settings.value.referencePitchHz,
            1e-9,
        )

        viewModel.setReferencePitch(100.0)
        assertEquals(
            UserSettings.REFERENCE_PITCH_RANGE.start,
            viewModel.settings.value.referencePitchHz,
            1e-9,
        )
    }

    @Test
    fun `a tolerance outside the supported range is clamped`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeTunerRepository()
        val viewModel = viewModel(repository)
        observe(viewModel)

        viewModel.setToleranceCents(99)
        assertEquals(UserSettings.TOLERANCE_RANGE.last, viewModel.settings.value.toleranceCents)

        viewModel.setToleranceCents(0)
        assertEquals(UserSettings.TOLERANCE_RANGE.first, viewModel.settings.value.toleranceCents)
    }
}
