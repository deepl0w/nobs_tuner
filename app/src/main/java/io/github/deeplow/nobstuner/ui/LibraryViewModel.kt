package io.github.deeplow.nobstuner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.deeplow.nobstuner.appContainer
import io.github.deeplow.nobstuner.data.TunerRepository
import io.github.deeplow.nobstuner.model.InstrumentFamily
import io.github.deeplow.nobstuner.model.Tuning
import io.github.deeplow.nobstuner.model.TuningCatalog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class LibraryState(
    val favorites: List<Tuning> = emptyList(),
    val presetsByFamily: Map<InstrumentFamily, List<Tuning>> = emptyMap(),
    val customTunings: List<Tuning> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val selectedId: String = TuningCatalog.default.id,
    val useFlats: Boolean = false,
)

/**
 * Drives the tuning library and the custom-tuning editor: browsing presets,
 * favourites, and creating or deleting the player's own tunings.
 *
 * Separate from [TunerViewModel] because none of this has anything to do with
 * listening to a microphone. They stay in step through [TunerRepository], which
 * both of them read.
 */
class LibraryViewModel(private val repository: TunerRepository) : ViewModel() {

    val state: StateFlow<LibraryState> = combine(
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

    fun selectTuning(id: String) = viewModelScope.launch { repository.selectTuning(id) }

    fun toggleFavorite(id: String) = viewModelScope.launch { repository.toggleFavorite(id) }

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

    /**
     * Looks a tuning up by id for the editor. Reads the last emitted library
     * state, which the app-level collector keeps current for as long as there
     * is a screen on top of it.
     */
    fun findTuning(id: String): Tuning? =
        TuningCatalog.findById(id) ?: state.value.customTunings.firstOrNull { it.id == id }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(appContainer().repository) }
        }
    }
}
