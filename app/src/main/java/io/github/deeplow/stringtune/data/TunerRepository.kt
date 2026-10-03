package io.github.deeplow.stringtune.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.deeplow.stringtune.model.Tuning
import io.github.deeplow.stringtune.model.TuningCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stringtune")

/**
 * Single source of truth for user data: settings, saved custom tunings, the
 * favourites set and which tuning is currently selected.
 *
 * Custom tunings are stored as a JSON blob in one preference key rather than in
 * a database. There are tens of them at most, they are always read and written
 * whole, and this keeps the app free of a schema to migrate.
 */
class TunerRepository(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val referencePitch = doublePreferencesKey("reference_pitch_hz")
        val useFlats = booleanPreferencesKey("use_flats")
        val autoDetectString = booleanPreferencesKey("auto_detect_string")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val themeMode = stringPreferencesKey("theme_mode")
        val toleranceCents = intPreferencesKey("tolerance_cents")

        val favorites = stringSetPreferencesKey("favorite_tuning_ids")
        val customTunings = stringPreferencesKey("custom_tunings_json")
        val selectedTuning = stringPreferencesKey("selected_tuning_id")
        val chromaticMode = booleanPreferencesKey("chromatic_mode")
    }

    /** A corrupt or unreadable store should not crash the app — fall back to defaults. */
    private val preferences: Flow<Preferences> = dataStore.data
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }

    val settings: Flow<UserSettings> = preferences.map { prefs ->
        val defaults = UserSettings()
        UserSettings(
            referencePitchHz = prefs[Keys.referencePitch]
                ?.coerceIn(UserSettings.REFERENCE_PITCH_RANGE)
                ?: defaults.referencePitchHz,
            useFlats = prefs[Keys.useFlats] ?: defaults.useFlats,
            autoDetectString = prefs[Keys.autoDetectString] ?: defaults.autoDetectString,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: defaults.keepScreenOn,
            themeMode = prefs[Keys.themeMode]
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: defaults.themeMode,
            toleranceCents = prefs[Keys.toleranceCents]
                ?.coerceIn(UserSettings.TOLERANCE_RANGE)
                ?: defaults.toleranceCents,
        )
    }

    val customTunings: Flow<List<Tuning>> = preferences.map { prefs ->
        decodeCustomTunings(prefs[Keys.customTunings])
    }

    val favoriteIds: Flow<Set<String>> = preferences.map { prefs ->
        prefs[Keys.favorites] ?: emptySet()
    }

    val selectedTuningId: Flow<String> = preferences.map { prefs ->
        prefs[Keys.selectedTuning] ?: TuningCatalog.default.id
    }

    val chromaticMode: Flow<Boolean> = preferences.map { prefs ->
        prefs[Keys.chromaticMode] ?: false
    }

    // ---- Settings writes -------------------------------------------------

    suspend fun setReferencePitch(hz: Double) = edit {
        it[Keys.referencePitch] = hz.coerceIn(UserSettings.REFERENCE_PITCH_RANGE)
    }

    suspend fun setUseFlats(value: Boolean) = edit { it[Keys.useFlats] = value }

    suspend fun setAutoDetectString(value: Boolean) = edit { it[Keys.autoDetectString] = value }

    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.keepScreenOn] = value }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.themeMode] = mode.name }

    suspend fun setToleranceCents(cents: Int) = edit {
        it[Keys.toleranceCents] = cents.coerceIn(UserSettings.TOLERANCE_RANGE)
    }

    suspend fun setChromaticMode(value: Boolean) = edit { it[Keys.chromaticMode] = value }

    suspend fun selectTuning(id: String) = edit {
        it[Keys.selectedTuning] = id
        it[Keys.chromaticMode] = false
    }

    // ---- Favourites ------------------------------------------------------

    suspend fun toggleFavorite(id: String) = edit { prefs ->
        val current = prefs[Keys.favorites] ?: emptySet()
        prefs[Keys.favorites] = if (id in current) current - id else current + id
    }

    // ---- Custom tunings --------------------------------------------------

    /** Inserts a new custom tuning or replaces the existing one with the same id. */
    suspend fun saveCustomTuning(tuning: Tuning) = edit { prefs ->
        val stored = decodeCustomTunings(prefs[Keys.customTunings])
        val normalised = tuning.copy(isCustom = true)
        val updated = stored.filterNot { it.id == normalised.id } + normalised
        prefs[Keys.customTunings] = json.encodeToString(updated.sortedBy { it.name.lowercase() })
    }

    suspend fun deleteCustomTuning(id: String) = edit { prefs ->
        val stored = decodeCustomTunings(prefs[Keys.customTunings])
        prefs[Keys.customTunings] = json.encodeToString(stored.filterNot { it.id == id })
        prefs[Keys.favorites] = (prefs[Keys.favorites] ?: emptySet()) - id
        // Selecting a deleted tuning would leave the tuner with nothing to aim at.
        if (prefs[Keys.selectedTuning] == id) {
            prefs[Keys.selectedTuning] = TuningCatalog.default.id
        }
    }

    private fun decodeCustomTunings(raw: String?): List<Tuning> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Tuning>>(raw) }
            .getOrElse { emptyList() }
            .map { it.copy(isCustom = true) }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }
}
