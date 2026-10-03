package io.github.deeplow.stringtune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.deeplow.stringtune.model.InstrumentFamily
import io.github.deeplow.stringtune.model.Notes
import io.github.deeplow.stringtune.model.Tuning

/** Keeps the editor a readable column instead of a tablet-wide form. */
private fun Modifier.formWidth(): Modifier = this.widthIn(max = 560.dp).fillMaxWidth()

private const val MIN_STRINGS = 1
private const val MAX_STRINGS = 12

/**
 * Create or edit a custom tuning.
 *
 * [existing] is null when creating. [seedFrom] lets "duplicate" open the editor
 * pre-filled with another tuning's notes but no id, so saving makes a new entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTuningScreen(
    existing: Tuning?,
    seedFrom: Tuning?,
    useFlats: Boolean,
    onBack: () -> Unit,
    onSave: (existingId: String?, name: String, family: InstrumentFamily, strings: List<Int>) -> Unit,
) {
    val template = existing ?: seedFrom
    val initialFamily = template?.family ?: InstrumentFamily.GUITAR
    val initialStrings = template?.strings ?: initialFamily.seedStrings

    var name by rememberSaveable {
        mutableStateOf(
            when {
                existing != null -> existing.name
                seedFrom != null -> "${seedFrom.name} copy"
                else -> ""
            },
        )
    }
    var familyName by rememberSaveable { mutableStateOf(initialFamily.name) }
    val family = InstrumentFamily.entries.first { it.name == familyName }

    val stringsState = rememberSaveable(
        saver = listSaver(
            save = { state -> state.value },
            restore = { restored -> mutableStateOf(restored) },
        ),
    ) { mutableStateOf(initialStrings) }
    var strings by stringsState

    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing != null) "Edit tuning" else "New tuning") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Discard and go back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onSave(existing?.id, name, family, strings) },
                        enabled = strings.isNotEmpty(),
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Open C♯ for slide") },
                    singleLine = true,
                    modifier = Modifier.formWidth(),
                )
            }

            item {
                FamilyPicker(
                    selected = family,
                    onSelect = { picked ->
                        familyName = picked.name
                        // Only reshape the strings if the user has not started
                        // from an existing tuning; otherwise their notes would
                        // vanish on a stray tap.
                        if (template == null) strings = picked.seedStrings
                    },
                )
            }

            item {
                StringCountRow(
                    count = strings.size,
                    onDecrease = { strings = strings.dropLast(1) },
                    onIncrease = {
                        val last = strings.lastOrNull() ?: 40
                        strings = strings + (last + 5).coerceAtMost(Notes.MAX_MIDI)
                    },
                )
            }

            itemsIndexed(strings, key = { index, _ -> "string_$index" }) { index, midi ->
                StringEditorRow(
                    stringNumber = strings.size - index,
                    midi = midi,
                    useFlats = useFlats,
                    onNudge = { delta ->
                        strings = strings.toMutableList().also {
                            it[index] = (midi + delta).coerceIn(Notes.MIN_MIDI, Notes.MAX_MIDI)
                        }
                    },
                    onPick = { editingIndex = index },
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Strings are listed the way you count them on the instrument — " +
                        "string ${strings.size} first. Re-entrant tunings are fine; " +
                        "the notes do not have to ascend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    editingIndex?.let { index ->
        NotePickerDialog(
            initialMidi = strings[index],
            useFlats = useFlats,
            onDismiss = { editingIndex = null },
            onConfirm = { picked ->
                strings = strings.toMutableList().also { it[index] = picked }
                editingIndex = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamilyPicker(selected: InstrumentFamily, onSelect: (InstrumentFamily) -> Unit) {
    Column(Modifier.formWidth()) {
        Text(
            text = "Instrument",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InstrumentFamily.entries.forEach { family ->
                FilterChip(
                    selected = family == selected,
                    onClick = { onSelect(family) },
                    label = { Text(family.displayName) },
                )
            }
        }
    }
}

@Composable
private fun StringCountRow(count: Int, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.formWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Strings", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(16.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(onClick = onDecrease, enabled = count > MIN_STRINGS) {
                Icon(Icons.Filled.Remove, contentDescription = "Remove a string")
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onIncrease, enabled = count < MAX_STRINGS) {
                Icon(Icons.Filled.Add, contentDescription = "Add a string")
            }
        }
    }
}

@Composable
private fun StringEditorRow(
    stringNumber: Int,
    midi: Int,
    useFlats: Boolean,
    onNudge: (Int) -> Unit,
    onPick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.formWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringNumber.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp),
            )
            OutlinedButton(onClick = onPick, modifier = Modifier.width(96.dp)) {
                Text(
                    text = Notes.name(midi, useFlats),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "%.1f Hz".format(Notes.frequencyOf(midi)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onNudge(-1) }, enabled = midi > Notes.MIN_MIDI) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Lower by a semitone")
            }
            IconButton(onClick = { onNudge(1) }, enabled = midi < Notes.MAX_MIDI) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Raise by a semitone")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotePickerDialog(
    initialMidi: Int,
    useFlats: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var pitchClass by remember { mutableIntStateOf(Math.floorMod(initialMidi, 12)) }
    var octave by remember { mutableIntStateOf(Notes.octaveOf(initialMidi)) }

    val midi = (octave + 1) * 12 + pitchClass
    val valid = midi in Notes.MIN_MIDI..Notes.MAX_MIDI

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a note") },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(12) { index ->
                        FilterChip(
                            selected = index == pitchClass,
                            onClick = { pitchClass = index },
                            label = { Text(Notes.pitchClassName(index, useFlats)) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Octave", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..8).forEach { value ->
                        val candidate = (value + 1) * 12 + pitchClass
                        FilterChip(
                            selected = value == octave,
                            enabled = candidate in Notes.MIN_MIDI..Notes.MAX_MIDI,
                            onClick = { octave = value },
                            label = { Text(value.toString()) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (valid) {
                    Text(
                        text = "${Notes.name(midi, useFlats)} · %.2f Hz".format(
                            Notes.frequencyOf(midi),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(midi) }, enabled = valid) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
