package io.github.deeplow.nobstuner.ui

import io.github.deeplow.nobstuner.model.InstrumentFamily
import io.github.deeplow.nobstuner.model.Tuning
import io.github.deeplow.nobstuner.model.TuningCatalog
import io.github.deeplow.nobstuner.testing.FakeTunerRepository
import io.github.deeplow.nobstuner.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun TestScope.observe(viewModel: LibraryViewModel) {
        backgroundScope.launch { viewModel.state.collect {} }
    }

    @Test
    fun `presets are grouped by instrument family`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = LibraryViewModel(FakeTunerRepository())
        observe(viewModel)

        val grouped = viewModel.state.value.presetsByFamily
        assertEquals(InstrumentFamily.entries.count { family ->
            TuningCatalog.presets.any { it.family == family }
        }, grouped.size)
        assertTrue(grouped.getValue(InstrumentFamily.GUITAR).isNotEmpty())
    }

    @Test
    fun `favourites span presets and custom tunings`() = runTest(mainDispatcher.dispatcher) {
        val custom = Tuning("custom_1", "Mine", InstrumentFamily.OTHER, listOf(40, 45))
        val repository = FakeTunerRepository(
            initialCustomTunings = listOf(custom),
            initialFavorites = setOf("guitar_drop_d", "custom_1"),
        )
        val viewModel = LibraryViewModel(repository)
        observe(viewModel)

        val favouriteIds = viewModel.state.value.favorites.map { it.id }.toSet()
        assertEquals(setOf("guitar_drop_d", "custom_1"), favouriteIds)
    }

    @Test
    fun `selecting a tuning leaves chromatic mode`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeTunerRepository(initialChromatic = true)
        val viewModel = LibraryViewModel(repository)
        observe(viewModel)

        viewModel.selectTuning("guitar_drop_d")

        assertEquals("guitar_drop_d", repository.currentSelectedId)
        assertFalse(repository.currentChromatic)
    }

    @Test
    fun `saving a new tuning gives it an id and selects it`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeTunerRepository()
        val viewModel = LibraryViewModel(repository)
        observe(viewModel)

        viewModel.saveCustomTuning(null, "My tuning", InstrumentFamily.GUITAR, listOf(40, 45, 50))

        val saved = repository.currentCustomTunings.single()
        assertTrue(saved.id.startsWith("custom_"))
        assertEquals("My tuning", saved.name)
        assertTrue(saved.isCustom)
        assertEquals(saved.id, repository.currentSelectedId)
    }

    @Test
    fun `saving over an existing id replaces rather than duplicates`() = runTest(mainDispatcher.dispatcher) {
        val existing = Tuning("custom_1", "Old", InstrumentFamily.OTHER, listOf(40))
        val repository = FakeTunerRepository(initialCustomTunings = listOf(existing))
        val viewModel = LibraryViewModel(repository)
        observe(viewModel)

        viewModel.saveCustomTuning("custom_1", "New", InstrumentFamily.OTHER, listOf(41))

        val saved = repository.currentCustomTunings.single()
        assertEquals("custom_1", saved.id)
        assertEquals("New", saved.name)
    }

    @Test
    fun `a blank name falls back to a placeholder`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeTunerRepository()
        val viewModel = LibraryViewModel(repository)
        observe(viewModel)

        viewModel.saveCustomTuning(null, "   ", InstrumentFamily.GUITAR, listOf(40))

        assertEquals("Untitled tuning", repository.currentCustomTunings.single().name)
    }

    @Test
    fun `deleting the selected tuning falls back to the default`() = runTest(mainDispatcher.dispatcher) {
        val custom = Tuning("custom_1", "Mine", InstrumentFamily.OTHER, listOf(40))
        val repository = FakeTunerRepository(
            initialCustomTunings = listOf(custom),
            initialFavorites = setOf("custom_1"),
            initialSelectedId = "custom_1",
        )
        val viewModel = LibraryViewModel(repository)
        observe(viewModel)

        viewModel.deleteCustomTuning("custom_1")

        assertTrue(repository.currentCustomTunings.isEmpty())
        assertEquals(TuningCatalog.default.id, repository.currentSelectedId)
        assertTrue("a deleted tuning must not linger in favourites", repository.currentFavorites.isEmpty())
    }

    @Test
    fun `findTuning resolves presets and custom tunings`() = runTest(mainDispatcher.dispatcher) {
        val custom = Tuning("custom_1", "Mine", InstrumentFamily.OTHER, listOf(40))
        val viewModel = LibraryViewModel(FakeTunerRepository(initialCustomTunings = listOf(custom)))
        observe(viewModel)

        assertEquals("Standard", viewModel.findTuning("guitar_standard")?.name)
        assertEquals("Mine", viewModel.findTuning("custom_1")?.name)
        assertNull(viewModel.findTuning("nope"))
    }

    @Test
    fun `toggling a favourite is reversible`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeTunerRepository()
        val viewModel = LibraryViewModel(repository)
        observe(viewModel)

        viewModel.toggleFavorite("guitar_drop_d")
        assertEquals(setOf("guitar_drop_d"), repository.currentFavorites)

        viewModel.toggleFavorite("guitar_drop_d")
        assertTrue(repository.currentFavorites.isEmpty())
    }
}
