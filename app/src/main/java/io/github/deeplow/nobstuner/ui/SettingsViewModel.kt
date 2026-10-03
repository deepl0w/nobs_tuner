package io.github.deeplow.nobstuner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.deeplow.nobstuner.appContainer
import io.github.deeplow.nobstuner.data.DisplayStyle
import io.github.deeplow.nobstuner.data.ThemeMode
import io.github.deeplow.nobstuner.data.TunerRepository
import io.github.deeplow.nobstuner.data.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Everything the user can change in Settings.
 *
 * Held at the top of the UI rather than by the settings screen alone, because
 * the theme and the keep-awake flag apply to the whole app and have to outlive
 * a visit to that screen.
 */
class SettingsViewModel(private val repository: TunerRepository) : ViewModel() {

    val settings: StateFlow<UserSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun setReferencePitch(hz: Double) = viewModelScope.launch { repository.setReferencePitch(hz) }

    fun setUseFlats(value: Boolean) = viewModelScope.launch { repository.setUseFlats(value) }

    fun setAutoDetectString(value: Boolean) =
        viewModelScope.launch { repository.setAutoDetectString(value) }

    fun setKeepScreenOn(value: Boolean) =
        viewModelScope.launch { repository.setKeepScreenOn(value) }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repository.setThemeMode(mode) }

    fun setToleranceCents(cents: Int) =
        viewModelScope.launch { repository.setToleranceCents(cents) }

    fun setDisplayStyle(style: DisplayStyle) =
        viewModelScope.launch { repository.setDisplayStyle(style) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(appContainer().repository) }
        }
    }
}
