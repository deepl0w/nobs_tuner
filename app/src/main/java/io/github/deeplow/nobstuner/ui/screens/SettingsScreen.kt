package io.github.deeplow.nobstuner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.deeplow.nobstuner.data.DisplayStyle
import io.github.deeplow.nobstuner.data.ThemeMode
import io.github.deeplow.nobstuner.data.UserSettings
import io.github.deeplow.nobstuner.ui.theme.MonoNumeric
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    appVersion: String,
    onBack: () -> Unit,
    onReferencePitchChange: (Double) -> Unit,
    onUseFlatsChange: (Boolean) -> Unit,
    onToleranceChange: (Int) -> Unit,
    onAutoDetectChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDisplayStyleChange: (DisplayStyle) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to tuner")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
          ) {
            SectionTitle("Pitch")

            SettingRow(
                title = "Reference pitch",
                subtitle = "The frequency of A4 that everything else is measured against.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(
                        onClick = { onReferencePitchChange(settings.referencePitchHz - 1) },
                        enabled = settings.referencePitchHz >
                            UserSettings.REFERENCE_PITCH_RANGE.start,
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Lower reference pitch")
                    }
                    Text(
                        text = "%.0f Hz".format(settings.referencePitchHz),
                        style = MaterialTheme.typography.titleMedium.merge(MonoNumeric),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(96.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    FilledTonalIconButton(
                        onClick = { onReferencePitchChange(settings.referencePitchHz + 1) },
                        enabled = settings.referencePitchHz <
                            UserSettings.REFERENCE_PITCH_RANGE.endInclusive,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Raise reference pitch")
                    }
                    Spacer(Modifier.weight(1f))
                    if (settings.referencePitchHz != 440.0) {
                        TextButton(onClick = { onReferencePitchChange(440.0) }) { Text("Reset") }
                    }
                }
            }

            SettingRow(
                title = "In-tune tolerance",
                subtitle = "How many cents either side still counts as in tune " +
                    "(±${settings.toleranceCents}).",
            ) {
                Slider(
                    value = settings.toleranceCents.toFloat(),
                    onValueChange = { onToleranceChange(it.roundToInt()) },
                    valueRange = UserSettings.TOLERANCE_RANGE.first.toFloat()..
                        UserSettings.TOLERANCE_RANGE.last.toFloat(),
                    steps = UserSettings.TOLERANCE_RANGE.last - UserSettings.TOLERANCE_RANGE.first - 1,
                )
            }

            SettingRow(
                title = "Accidentals",
                subtitle = "How notes between the naturals are spelled.",
            ) {
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = !settings.useFlats,
                        onClick = { onUseFlatsChange(false) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("Sharps (A♯)") }
                    SegmentedButton(
                        selected = settings.useFlats,
                        onClick = { onUseFlatsChange(true) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("Flats (B♭)") }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("Tuner")

            ToggleRow(
                title = "Detect string automatically",
                subtitle = "Aim at whichever string you play instead of selecting one by hand.",
                checked = settings.autoDetectString,
                onCheckedChange = onAutoDetectChange,
            )

            ToggleRow(
                title = "Keep screen on",
                subtitle = "Stops the display sleeping while the tuner is open.",
                checked = settings.keepScreenOn,
                onCheckedChange = onKeepScreenOnChange,
            )

            SettingRow(
                title = "Display",
                subtitle = settings.displayStyle.description,
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DisplayStyle.entries.forEach { style ->
                        FilterChip(
                            selected = settings.displayStyle == style,
                            onClick = { onDisplayStyleChange(style) },
                            label = { Text(style.displayName) },
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("Appearance")

            SettingRow(title = "Theme", subtitle = null) {
                SingleChoiceSegmentedButtonRow {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { onThemeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ThemeMode.entries.size,
                            ),
                        ) {
                            Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("About")

            Text(
                text = "NobsTuner $appVersion",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Audio is analysed entirely on this device. Nothing is recorded, " +
                    "stored or sent anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
          }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
