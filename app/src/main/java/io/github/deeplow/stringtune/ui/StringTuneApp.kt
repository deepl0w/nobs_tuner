package io.github.deeplow.stringtune.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.deeplow.stringtune.model.Tuning
import io.github.deeplow.stringtune.ui.screens.CustomTuningScreen
import io.github.deeplow.stringtune.ui.screens.LibraryScreen
import io.github.deeplow.stringtune.ui.screens.SettingsScreen
import io.github.deeplow.stringtune.ui.screens.TunerScreen
import io.github.deeplow.stringtune.ui.theme.StringTuneTheme

private object Routes {
    const val TUNER = "tuner"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val EDITOR = "editor?editId={editId}&seedId={seedId}"

    fun editor(editId: String? = null, seedId: String? = null): String =
        "editor?editId=${editId.orEmpty()}&seedId=${seedId.orEmpty()}"
}

@Composable
fun StringTuneApp(appVersion: String) {
    val viewModel: TunerViewModel = viewModel(factory = TunerViewModel.Factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val tunedStrings by viewModel.tunedStrings.collectAsStateWithLifecycle()

    StringTuneTheme(themeMode = uiState.settings.themeMode) {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = Routes.TUNER) {
            composable(Routes.TUNER) {
                TunerScreen(
                    state = uiState,
                    tunedStrings = tunedStrings,
                    onStartListening = viewModel::startListening,
                    onStopListening = viewModel::stopListening,
                    onPermissionResult = viewModel::onPermissionResult,
                    onToggleChromatic = viewModel::setChromaticMode,
                    onToggleFavorite = { viewModel.toggleFavorite(uiState.tuning.id) },
                    onSelectString = viewModel::selectString,
                    onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(Routes.LIBRARY) {
                LibraryScreen(
                    state = libraryState,
                    onBack = navController::popBackStack,
                    onSelect = { id ->
                        viewModel.selectTuning(id)
                        navController.popBackStack()
                    },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onCreate = { navController.navigate(Routes.editor()) },
                    onEdit = { id -> navController.navigate(Routes.editor(editId = id)) },
                    onDuplicate = { tuning ->
                        navController.navigate(Routes.editor(seedId = tuning.id))
                    },
                    onDelete = viewModel::deleteCustomTuning,
                )
            }

            composable(
                route = Routes.EDITOR,
                arguments = listOf(
                    navArgument("editId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("seedId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStackEntry ->
                val editId = backStackEntry.arguments?.getString("editId").orEmpty()
                val seedId = backStackEntry.arguments?.getString("seedId").orEmpty()
                CustomTuningScreen(
                    existing = editId.takeIf { it.isNotEmpty() }?.let(viewModel::findTuning),
                    seedFrom = seedId.takeIf { it.isNotEmpty() }?.let(viewModel::findTuning),
                    useFlats = uiState.settings.useFlats,
                    onBack = navController::popBackStack,
                    onSave = { existingId, name, family, strings ->
                        viewModel.saveCustomTuning(existingId, name, family, strings)
                        // Straight back to the tuner: saving selects the tuning,
                        // and the library in between would just be a flash.
                        navController.popBackStack(Routes.TUNER, inclusive = false)
                    },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settings = uiState.settings,
                    appVersion = appVersion,
                    onBack = navController::popBackStack,
                    onReferencePitchChange = viewModel::setReferencePitch,
                    onUseFlatsChange = viewModel::setUseFlats,
                    onToleranceChange = viewModel::setToleranceCents,
                    onAutoDetectChange = viewModel::setAutoDetectString,
                    onKeepScreenOnChange = viewModel::setKeepScreenOn,
                    onThemeChange = viewModel::setThemeMode,
                )
            }
        }
    }
}
