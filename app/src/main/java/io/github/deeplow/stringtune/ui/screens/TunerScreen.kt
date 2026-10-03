package io.github.deeplow.stringtune.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.deeplow.stringtune.model.Notes
import io.github.deeplow.stringtune.ui.TunerUiState
import io.github.deeplow.stringtune.ui.components.HintText
import io.github.deeplow.stringtune.ui.components.InputLevelBar
import io.github.deeplow.stringtune.ui.components.NoteReadout
import io.github.deeplow.stringtune.ui.components.StringSelector
import io.github.deeplow.stringtune.ui.components.TuningMeter
import io.github.deeplow.stringtune.ui.components.WindowShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerScreen(
    state: TunerUiState,
    tunedStrings: Set<Int>,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onPermissionResult: (Boolean) -> Unit,
    onToggleChromatic: (Boolean) -> Unit,
    onToggleFavorite: () -> Unit,
    onSelectString: (Int?) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        onResult = onPermissionResult,
    )

    // Ask once on first show; afterwards the in-screen button drives it.
    LaunchedEffect(Unit) {
        if (!state.micPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Hold the mic only while this screen is actually in front of the user.
    LifecycleResumeEffect(Unit) {
        onStartListening()
        onPauseOrDispose { onStopListening() }
    }

    val view = LocalView.current
    LaunchedEffect(state.settings.keepScreenOn) {
        view.keepScreenOn = state.settings.keepScreenOn
    }

    val requestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.chromaticMode) "Chromatic" else state.tuning.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (!state.chromaticMode) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (state.isFavorite) {
                                    Icons.Filled.Star
                                } else {
                                    Icons.Outlined.StarBorder
                                },
                                contentDescription = if (state.isFavorite) {
                                    "Remove ${state.tuning.name} from favourites"
                                } else {
                                    "Add ${state.tuning.name} to favourites"
                                },
                            )
                        }
                    }
                    IconButton(onClick = onOpenLibrary) {
                        Icon(Icons.Outlined.LibraryMusic, contentDescription = "Tuning library")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val shape = WindowShape(width = maxWidth, height = maxHeight)
            if (shape.prefersSideBySide) {
                SideBySideTuner(
                    state = state,
                    tunedStrings = tunedStrings,
                    shape = shape,
                    onToggleChromatic = onToggleChromatic,
                    onSelectString = onSelectString,
                    onRequestPermission = { requestPermission() },
                    onRetryAudio = onStartListening,
                )
            } else {
                StackedTuner(
                    state = state,
                    tunedStrings = tunedStrings,
                    shape = shape,
                    onToggleChromatic = onToggleChromatic,
                    onSelectString = onSelectString,
                    onRequestPermission = { requestPermission() },
                    onRetryAudio = onStartListening,
                )
            }
        }
    }
}

/** Phone portrait and tablet portrait: one centred column. */
@Composable
private fun StackedTuner(
    state: TunerUiState,
    tunedStrings: Set<Int>,
    shape: WindowShape,
    onToggleChromatic: (Boolean) -> Unit,
    onSelectString: (Int?) -> Unit,
    onRequestPermission: () -> Unit,
    onRetryAudio: () -> Unit,
) {
    val tight = shape.isTightHeight
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        // Centres the block on a tall tablet; on a short phone the content is
        // taller than the viewport and this has no effect beyond scrolling.
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = shape.readingMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (tight) 4.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ModeSwitch(state, onToggleChromatic)
            Spacer(Modifier.height(if (tight) 8.dp else 16.dp))
            Meter(state)
            Spacer(Modifier.height(if (tight) 6.dp else 12.dp))
            Readout(state, compact = tight)
            Spacer(Modifier.height(if (tight) 12.dp else 20.dp))
            Strings(state, tunedStrings, onSelectString)
            Spacer(Modifier.height(if (tight) 8.dp else 12.dp))
            Status(state, onRequestPermission, onRetryAudio)
            Spacer(Modifier.height(if (tight) 12.dp else 24.dp))
        }
    }
}

/** Landscape phones and large tablets: dial on one side, controls on the other. */
@Composable
private fun SideBySideTuner(
    state: TunerUiState,
    tunedStrings: Set<Int>,
    shape: WindowShape,
    onToggleChromatic: (Boolean) -> Unit,
    onSelectString: (Int?) -> Unit,
    onRequestPermission: () -> Unit,
    onRetryAudio: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val paneModifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
        val paneContent = Modifier.widthIn(max = shape.readingMaxWidth).fillMaxWidth()

        Column(
            modifier = paneModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(paneContent, horizontalAlignment = Alignment.CenterHorizontally) {
                Meter(state, maxWidth = meterWidthFor(shape))
                Spacer(Modifier.height(8.dp))
                Readout(state, compact = shape.isShort)
            }
        }
        Column(
            modifier = paneModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(paneContent, horizontalAlignment = Alignment.CenterHorizontally) {
                ModeSwitch(state, onToggleChromatic)
                Spacer(Modifier.height(20.dp))
                Strings(state, tunedStrings, onSelectString)
                Spacer(Modifier.height(12.dp))
                Status(state, onRequestPermission, onRetryAudio)
            }
        }
    }
}

// ---- Shared pieces -------------------------------------------------------

@Composable
private fun Meter(state: TunerUiState, maxWidth: Dp = Dp.Unspecified) {
    TuningMeter(
        cents = state.reading?.cents,
        toleranceCents = state.settings.toleranceCents,
        maxWidth = maxWidth,
    )
}

/**
 * Widest the dial may be before it stops fitting the height it has. The dial is
 * 1.85 times as wide as it is tall, and the readout under it needs its own
 * room, so on a landscape phone the limit comes from the height, not the width.
 */
private fun meterWidthFor(shape: WindowShape): Dp {
    val READOUT_SPACE = 150.dp
    val fromHeight = (shape.height - READOUT_SPACE) * 1.85f
    return minOf(shape.readingMaxWidth, fromHeight).coerceAtLeast(160.dp)
}

@Composable
private fun Readout(state: TunerUiState, compact: Boolean = false) {
    NoteReadout(
        targetMidi = state.reading?.targetMidi,
        cents = state.reading?.cents,
        detectedHz = state.reading?.frequencyHz,
        targetHz = state.reading?.targetMidi?.let {
            Notes.frequencyOf(it, state.settings.referencePitchHz)
        },
        toleranceCents = state.settings.toleranceCents,
        useFlats = state.settings.useFlats,
        compact = compact,
    )
}

@Composable
private fun Strings(
    state: TunerUiState,
    tunedStrings: Set<Int>,
    onSelectString: (Int?) -> Unit,
) {
    if (state.chromaticMode) return
    StringSelector(
        tuning = state.tuning,
        activeIndex = state.reading?.stringIndex,
        pinnedIndex = state.manualStringIndex,
        tunedIndices = tunedStrings,
        useFlats = state.settings.useFlats,
        onSelect = onSelectString,
    )
    Spacer(Modifier.height(10.dp))
    HintText(
        text = if (state.manualStringIndex != null) {
            "Listening for one string. Tap it again for automatic detection."
        } else {
            "Tap a string to lock onto it."
        },
    )
}

@Composable
private fun Status(
    state: TunerUiState,
    onRequestPermission: () -> Unit,
    onRetryAudio: () -> Unit,
) {
    when {
        !state.micPermissionGranted -> MicrophoneNotice(
            message = "StringTune needs the microphone to hear your instrument.",
            actionLabel = "Grant access",
            onAction = onRequestPermission,
        )
        state.audioError != null -> MicrophoneNotice(
            message = state.audioError,
            actionLabel = "Try again",
            onAction = onRetryAudio,
        )
        else -> InputLevelBar(
            levelDbfs = state.reading?.levelDbfs,
            modifier = Modifier.padding(horizontal = 40.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSwitch(state: TunerUiState, onToggleChromatic: (Boolean) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = !state.chromaticMode,
                onClick = { onToggleChromatic(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Tuning") }
            SegmentedButton(
                selected = state.chromaticMode,
                onClick = { onToggleChromatic(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Chromatic") }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (state.chromaticMode) {
                "Any note"
            } else {
                state.tuning.detailedSummary(state.settings.useFlats)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MicrophoneNotice(message: String, actionLabel: String, onAction: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
