package io.github.deeplow.nobstuner.data

import io.github.deeplow.nobstuner.model.Tuning
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for user data: settings, saved custom tunings, the
 * favourites set, which tuning is selected and whether chromatic mode is on.
 *
 * Every view model reads and writes through this interface, so two screens
 * observing the same fact always agree. [DataStoreTunerRepository] is the real
 * implementation; the interface exists so view models can be tested against an
 * in-memory fake rather than a DataStore on disk.
 */
interface TunerRepository {

    val settings: Flow<UserSettings>
    val customTunings: Flow<List<Tuning>>
    val favoriteIds: Flow<Set<String>>
    val selectedTuningId: Flow<String>
    val chromaticMode: Flow<Boolean>

    /** Clamped to [UserSettings.REFERENCE_PITCH_RANGE]. */
    suspend fun setReferencePitch(hz: Double)
    suspend fun setUseFlats(value: Boolean)
    suspend fun setAutoDetectString(value: Boolean)
    suspend fun setKeepScreenOn(value: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    /** Clamped to [UserSettings.TOLERANCE_RANGE]. */
    suspend fun setToleranceCents(cents: Int)
    suspend fun setDisplayStyle(style: DisplayStyle)
    suspend fun setChromaticMode(value: Boolean)

    /** Selects [id] and leaves chromatic mode. */
    suspend fun selectTuning(id: String)

    suspend fun toggleFavorite(id: String)

    /** Inserts a new custom tuning or replaces the existing one with the same id. */
    suspend fun saveCustomTuning(tuning: Tuning)

    suspend fun deleteCustomTuning(id: String)
}
