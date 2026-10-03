package io.github.deeplow.nobstuner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.deeplow.nobstuner.ui.screens.CustomTuningScreen
import io.github.deeplow.nobstuner.ui.screens.LibraryScreen
import io.github.deeplow.nobstuner.ui.screens.SettingsScreen
import io.github.deeplow.nobstuner.ui.screens.TunerScreen
import io.github.deeplow.nobstuner.ui.theme.NobsTunerTheme

private object Routes {
    const val TUNER = "tuner"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val EDITOR = "editor?editId={editId}&seedId={seedId}"

    fun editor(editId: String? = null, seedId: String? = null): String =
        "editor?editId=${editId.orEmpty()}&seedId=${seedId.orEmpty()}"
}

/**
 * The composition root of the UI.
 *
 * All three view models are obtained here, at the activity's scope, and their
 * state is handed down to screens as plain values. The screens themselves stay
 * free of view models, which is what lets them be previewed and read as
 * functions of their arguments.
 */
@Composable
fun NobsTunerApp(appVersion: String) {
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val tunerViewModel: TunerViewModel = viewModel(factory = TunerViewModel.Factory)
    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)

    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val uiState by tunerViewModel.uiState.collectAsStateWithLifecycle()
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val tunedStrings by tunerViewModel.tunedStrings.collectAsStateWithLifecycle()

    // Applied here rather than inside the tuner screen so that toggling it in
    // Settings takes effect straight away, and so the screen stays awake while
    // you are browsing tunings mid-session.
    val view = LocalView.current
    LaunchedEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
    }

    NobsTunerTheme(themeMode = settings.themeMode) {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = Routes.TUNER) {
            composable(Routes.TUNER) {
                TunerScreen(
                    state = uiState,
                    tunedStrings = tunedStrings,
                    onStartListening = tunerViewModel::startListening,
                    onStopListening = tunerViewModel::stopListening,
                    onPermissionResult = tunerViewModel::onPermissionResult,
                    onToggleChromatic = tunerViewModel::setChromaticMode,
                    onToggleFavorite = { tunerViewModel.toggleFavorite(uiState.tuning.id) },
                    onSelectString = tunerViewModel::selectString,
                    onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(Routes.LIBRARY) {
                LibraryScreen(
                    state = libraryState,
                    onBack = navController::popBackStack,
                    onSelect = { id ->
                        libraryViewModel.selectTuning(id)
                        navController.popBackStack()
                    },
                    onToggleFavorite = libraryViewModel::toggleFavorite,
                    onCreate = { navController.navigate(Routes.editor()) },
                    onEdit = { id -> navController.navigate(Routes.editor(editId = id)) },
                    onDuplicate = { tuning ->
                        navController.navigate(Routes.editor(seedId = tuning.id))
                    },
                    onDelete = libraryViewModel::deleteCustomTuning,
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
                    existing = editId.takeIf { it.isNotEmpty() }
                        ?.let(libraryViewModel::findTuning),
                    seedFrom = seedId.takeIf { it.isNotEmpty() }
                        ?.let(libraryViewModel::findTuning),
                    useFlats = settings.useFlats,
                    onBack = navController::popBackStack,
                    onSave = { existingId, name, family, strings ->
                        libraryViewModel.saveCustomTuning(existingId, name, family, strings)
                        // Straight back to the tuner: saving selects the tuning,
                        // and the library in between would just be a flash.
                        navController.popBackStack(Routes.TUNER, inclusive = false)
                    },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settings = settings,
                    appVersion = appVersion,
                    onBack = navController::popBackStack,
                    onReferencePitchChange = settingsViewModel::setReferencePitch,
                    onUseFlatsChange = settingsViewModel::setUseFlats,
                    onToleranceChange = settingsViewModel::setToleranceCents,
                    onDisplayStyleChange = settingsViewModel::setDisplayStyle,
                    onAutoDetectChange = settingsViewModel::setAutoDetectString,
                    onKeepScreenOnChange = settingsViewModel::setKeepScreenOn,
                    onThemeChange = settingsViewModel::setThemeMode,
                )
            }
        }
    }
}
