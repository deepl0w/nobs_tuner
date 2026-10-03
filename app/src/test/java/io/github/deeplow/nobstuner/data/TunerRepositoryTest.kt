package io.github.deeplow.nobstuner.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.deeplow.nobstuner.model.InstrumentFamily
import io.github.deeplow.nobstuner.model.Tuning
import io.github.deeplow.nobstuner.model.TuningCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The repository over a real file-backed preference store.
 *
 * The behaviour worth guarding is not just "a write comes back out" but that a
 * write to one setting stays out of the other flows: DataStore republishes the
 * entire snapshot each time, and anything downstream that treats an emission as
 * news will act on changes that never happened.
 */
class TunerRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var repository: TunerRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store = PreferenceDataStoreFactory.create(scope = scope) {
            folder.newFile("test.preferences_pb")
        }
        repository = TunerRepository(store)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** Records everything [flow] emits until the test is done with it. */
    private fun <T> record(flow: Flow<T>): Pair<MutableList<T>, Job> {
        val seen = mutableListOf<T>()
        val job = scope.launch { flow.collect { seen += it } }
        return seen to job
    }

    private suspend fun <T> awaitSize(seen: List<T>, size: Int) =
        withTimeout(5_000) { while (seen.size < size) delay(5) }

    /** Lets any further emissions land before we assert that none did. */
    private suspend fun settle() = delay(300)

    // ---- Emissions -------------------------------------------------------

    @Test
    fun `changing one setting does not re-emit from the other flows`() = runBlocking {
        val (chromatic, chromaticJob) = record(repository.chromaticMode)
        val (selected, selectedJob) = record(repository.selectedTuningId)
        val (favorites, favoritesJob) = record(repository.favoriteIds)
        val (custom, customJob) = record(repository.customTunings)
        awaitSize(chromatic, 1)
        awaitSize(selected, 1)
        awaitSize(favorites, 1)
        awaitSize(custom, 1)

        // Three unrelated settings writes, as a user poking at the Settings screen.
        repository.setToleranceCents(9)
        repository.setUseFlats(true)
        repository.setReferencePitch(442.0)
        repository.settings.first()
        settle()

        // Downstream, each extra emission clears the "already tuned" ticks and
        // drops the pinned string, so these have to stay at one.
        assertEquals("chromatic mode re-emitted", listOf(false), chromatic)
        assertEquals("selected tuning re-emitted", listOf(TuningCatalog.default.id), selected)
        assertEquals("favourites re-emitted", listOf(emptySet<String>()), favorites)
        assertEquals("custom tunings re-emitted", 1, custom.size)

        listOf(chromaticJob, selectedJob, favoritesJob, customJob).forEach { it.cancel() }
    }

    @Test
    fun `a setting written to its current value does not re-emit`() = runBlocking {
        repository.setToleranceCents(8)
        val (settings, job) = record(repository.settings)
        awaitSize(settings, 1)

        repository.setToleranceCents(8)
        repository.setToleranceCents(8)
        settle()

        assertEquals(1, settings.size)
        job.cancel()
    }

    @Test
    fun `a real change still comes through`() = runBlocking {
        val (settings, job) = record(repository.settings)
        awaitSize(settings, 1)

        repository.setToleranceCents(11)
        awaitSize(settings, 2)

        assertEquals(11, settings.last().toleranceCents)
        job.cancel()
    }

    // ---- Settings --------------------------------------------------------

    @Test
    fun `settings round trip`() = runBlocking {
        repository.setReferencePitch(442.0)
        repository.setUseFlats(true)
        repository.setAutoDetectString(false)
        repository.setKeepScreenOn(false)
        repository.setThemeMode(ThemeMode.DARK)
        repository.setToleranceCents(3)

        val settings = repository.settings.first()
        assertEquals(442.0, settings.referencePitchHz, 1e-9)
        assertTrue(settings.useFlats)
        assertEquals(false, settings.autoDetectString)
        assertEquals(false, settings.keepScreenOn)
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(3, settings.toleranceCents)
    }

    @Test
    fun `out of range settings are clamped on the way in`() = runBlocking {
        repository.setReferencePitch(1000.0)
        assertEquals(466.0, repository.settings.first().referencePitchHz, 1e-9)

        repository.setReferencePitch(100.0)
        assertEquals(415.0, repository.settings.first().referencePitchHz, 1e-9)

        repository.setToleranceCents(99)
        assertEquals(15, repository.settings.first().toleranceCents)

        repository.setToleranceCents(0)
        assertEquals(1, repository.settings.first().toleranceCents)
    }

    // ---- Tunings ---------------------------------------------------------

    @Test
    fun `selecting a tuning leaves chromatic mode`() = runBlocking {
        repository.setChromaticMode(true)
        assertTrue(repository.chromaticMode.first())

        repository.selectTuning("guitar_drop_d")
        assertEquals("guitar_drop_d", repository.selectedTuningId.first())
        assertEquals(false, repository.chromaticMode.first())
    }

    @Test
    fun `a custom tuning is saved, replaced and marked custom`() = runBlocking {
        val tuning = Tuning("custom_1", "My tuning", InstrumentFamily.GUITAR, listOf(40, 45, 50))
        repository.saveCustomTuning(tuning)

        val saved = repository.customTunings.first()
        assertEquals(1, saved.size)
        assertEquals("My tuning", saved.single().name)
        assertTrue("saved tuning lost its custom flag", saved.single().isCustom)

        repository.saveCustomTuning(tuning.copy(name = "Renamed"))
        val replaced = repository.customTunings.first()
        assertEquals("saving the same id twice duplicated it", 1, replaced.size)
        assertEquals("Renamed", replaced.single().name)
    }

    @Test
    fun `deleting a custom tuning clears its favourite and selection`() = runBlocking {
        val tuning = Tuning("custom_2", "Doomed", InstrumentFamily.BASS, listOf(28, 33, 38, 43))
        repository.saveCustomTuning(tuning)
        repository.toggleFavorite("custom_2")
        repository.selectTuning("custom_2")

        repository.deleteCustomTuning("custom_2")

        assertEquals(emptyList<Tuning>(), repository.customTunings.first())
        assertEquals(
            "a deleted tuning stayed in favourites",
            emptySet<String>(),
            repository.favoriteIds.first(),
        )
        assertEquals(
            "the tuner was left aiming at a tuning that no longer exists",
            TuningCatalog.default.id,
            repository.selectedTuningId.first(),
        )
    }

    @Test
    fun `favourites toggle on and off`() = runBlocking {
        repository.toggleFavorite("guitar_drop_d")
        assertEquals(setOf("guitar_drop_d"), repository.favoriteIds.first())

        repository.toggleFavorite("bass_standard")
        assertEquals(setOf("guitar_drop_d", "bass_standard"), repository.favoriteIds.first())

        repository.toggleFavorite("guitar_drop_d")
        assertEquals(setOf("bass_standard"), repository.favoriteIds.first())
    }

    @Test
    fun `custom tunings are sorted by name regardless of save order`() = runBlocking {
        repository.saveCustomTuning(Tuning("c1", "zebra", InstrumentFamily.OTHER, listOf(40)))
        repository.saveCustomTuning(Tuning("c2", "Alpha", InstrumentFamily.OTHER, listOf(40)))
        repository.saveCustomTuning(Tuning("c3", "middle", InstrumentFamily.OTHER, listOf(40)))

        assertEquals(
            listOf("Alpha", "middle", "zebra"),
            repository.customTunings.first().map { it.name },
        )
    }
}
