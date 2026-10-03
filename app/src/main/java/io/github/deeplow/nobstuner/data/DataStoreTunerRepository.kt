package io.github.deeplow.nobstuner.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.deeplow.nobstuner.model.Tuning
import io.github.deeplow.nobstuner.model.TuningCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nobstuner")

/**
 * [TunerRepository] backed by Preferences DataStore.
 *
 * Custom tunings are stored as a JSON blob in one preference key rather than in
 * a database. There are tens of them at most, they are always read and written
 * whole, and this keeps the app free of a schema to migrate.
 *
 * Every exposed flow drops repeats. DataStore republishes the whole preference
 * snapshot on each write, so without that a change to, say, the tolerance would
 * re-emit from all of them and anything downstream would treat it as news —
 * which is what used to clear the tuned-string ticks and unpin the selected
 * string whenever an unrelated setting changed.
 *
 * The store is injected so the mapping can be tested against a real DataStore
 * on a temporary file; [TunerRepositoryTest] does exactly that.
 */
class DataStoreTunerRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) : TunerRepository {

    constructor(context: Context) : this(context.applicationContext.dataStore)

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
        val displayStyle = stringPreferencesKey("display_style")

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

    override val settings: Flow<UserSettings> = preferences.map { prefs ->
        val defaults = UserSettings()
        UserSettings(
            referencePitchHz = prefs[Keys.referencePitch]
                ?.let(UserSettings::clampReferencePitch)
                ?: defaults.referencePitchHz,
            useFlats = prefs[Keys.useFlats] ?: defaults.useFlats,
            autoDetectString = prefs[Keys.autoDetectString] ?: defaults.autoDetectString,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: defaults.keepScreenOn,
            themeMode = prefs[Keys.themeMode]
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: defaults.themeMode,
            toleranceCents = prefs[Keys.toleranceCents]
                ?.let(UserSettings::clampToleranceCents)
                ?: defaults.toleranceCents,
            displayStyle = prefs[Keys.displayStyle]
                ?.let { name -> DisplayStyle.entries.firstOrNull { it.name == name } }
                ?: defaults.displayStyle,
        )
    }.distinctUntilChanged()

    override val customTunings: Flow<List<Tuning>> = preferences.map { prefs ->
        decodeCustomTunings(prefs[Keys.customTunings])
    }.distinctUntilChanged()

    override val favoriteIds: Flow<Set<String>> = preferences.map { prefs ->
        prefs[Keys.favorites] ?: emptySet()
    }.distinctUntilChanged()

    override val selectedTuningId: Flow<String> = preferences.map { prefs ->
        prefs[Keys.selectedTuning] ?: TuningCatalog.default.id
    }.distinctUntilChanged()

    override val chromaticMode: Flow<Boolean> = preferences.map { prefs ->
        prefs[Keys.chromaticMode] ?: false
    }.distinctUntilChanged()

    // ---- Settings writes -------------------------------------------------

    override suspend fun setReferencePitch(hz: Double) = edit {
        it[Keys.referencePitch] = UserSettings.clampReferencePitch(hz)
    }

    override suspend fun setUseFlats(value: Boolean) = edit { it[Keys.useFlats] = value }

    override suspend fun setAutoDetectString(value: Boolean) =
        edit { it[Keys.autoDetectString] = value }

    override suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.keepScreenOn] = value }

    override suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.themeMode] = mode.name }

    override suspend fun setToleranceCents(cents: Int) = edit {
        it[Keys.toleranceCents] = UserSettings.clampToleranceCents(cents)
    }

    override suspend fun setDisplayStyle(style: DisplayStyle) = edit {
        it[Keys.displayStyle] = style.name
    }

    override suspend fun setChromaticMode(value: Boolean) = edit {
        it[Keys.chromaticMode] = value
    }

    override suspend fun selectTuning(id: String) = edit {
        it[Keys.selectedTuning] = id
        it[Keys.chromaticMode] = false
    }

    // ---- Favourites ------------------------------------------------------

    override suspend fun toggleFavorite(id: String) = edit { prefs ->
        val current = prefs[Keys.favorites] ?: emptySet()
        prefs[Keys.favorites] = if (id in current) current - id else current + id
    }

    // ---- Custom tunings --------------------------------------------------

    override suspend fun saveCustomTuning(tuning: Tuning) = edit { prefs ->
        val stored = decodeCustomTunings(prefs[Keys.customTunings])
        val normalised = tuning.copy(isCustom = true)
        val updated = stored.filterNot { it.id == normalised.id } + normalised
        prefs[Keys.customTunings] = json.encodeToString(updated.sortedBy { it.name.lowercase() })
    }

    override suspend fun deleteCustomTuning(id: String) = edit { prefs ->
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

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }
}
