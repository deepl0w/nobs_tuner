/** Create or edit a custom tuning. */

import { el, icon, iconButton, appBar } from '../ui.js';

const MIN_STRINGS = 1;
const MAX_STRINGS = 12;

export function editorView(app, { editId, seedId }) {
  const { core, settings } = app;
  const existing = editId ? app.findTuning(editId) : null;
  const seed = seedId ? app.findTuning(seedId) : null;
  const template = existing || seed;

  let family = template ? template.family : 'GUITAR';
  let strings = (template ? template.strings : app.familySeed(family)).slice();
  let name = existing ? existing.name : seed ? `${seed.name} copy` : '';

  const stringList = el('div', { class: 'editor__strings', style: 'width:100%' });

  function save() {
    app.saveCustomTuning({ existingId: existing ? existing.id : null, name, family, strings });
  }

  const nameField = el('div', { class: 'field' }, [
    el('label', { for: 'tuning-name', text: 'Name' }),
    el('input', {
      id: 'tuning-name',
      type: 'text',
      value: name,
      placeholder: 'e.g. Open C♯ for slide',
      onInput: (event) => { name = event.target.value; },
    }),
  ]);

  const familyPicker = el('div', { class: 'field' }, [
    el('label', { text: 'Instrument', id: 'family-label' }),
    el(
      'div',
      { class: 'chip-row', role: 'group', 'aria-labelledby': 'family-label' },
      app.families.map((entry) =>
        el('button', {
          class: 'chip',
          type: 'button',
          text: entry.displayName,
          'aria-pressed': String(entry.name === family),
          onClick: () => {
            family = entry.name;
            // Only reshape the strings when the user started from scratch;
            // otherwise their notes would vanish on a stray tap.
            if (!template) strings = app.familySeed(family).slice();
            rebuild();
          },
        }),
      ),
    ),
  ]);

  const countValue = el('span', { class: 'count-row__value' });
  const removeButton = iconButton('remove', 'Remove a string', () => {
    strings = strings.slice(0, -1);
    rebuild();
  }, { class: 'icon-button--tonal' });
  const addButton = iconButton('add', 'Add a string', () => {
    const last = strings.length ? strings[strings.length - 1] : 40;
    strings = [...strings, Math.min(last + 5, core.maxMidi)];
    rebuild();
  }, { class: 'icon-button--tonal' });

  const countRow = el('div', { class: 'count-row' }, [
    el('span', { class: 'title-small', text: 'Strings' }),
    countValue,
    el('span', { class: 'count-row__spacer' }),
    removeButton,
    addButton,
  ]);

  function stringRow(midi, index) {
    const nudge = (delta) => {
      strings = strings.map((value, i) =>
        i === index ? Math.max(core.minMidi, Math.min(core.maxMidi, value + delta)) : value,
      );
      rebuild();
    };
    return el('div', { class: 'string-row' }, [
      el('span', { class: 'string-row__number', text: String(strings.length - index) }),
      el('button', {
        class: 'button button--outlined string-row__note',
        type: 'button',
        text: core.noteName(midi, settings.useFlats),
        'aria-label': `String ${strings.length - index}, ${core.noteName(midi, settings.useFlats)}. Choose a note`,
        onClick: () => openPicker(index),
      }),
      el('span', {
        class: 'body-small',
        text: `${core.noteFrequency(midi, settings.referencePitchHz).toFixed(1)} Hz`,
      }),
      el('span', { class: 'string-row__spacer' }),
      iconButton('arrowDown', 'Lower by a semitone', () => nudge(-1), {
        disabled: midi <= core.minMidi,
      }),
      iconButton('arrowUp', 'Raise by a semitone', () => nudge(1), {
        disabled: midi >= core.maxMidi,
      }),
    ]);
  }

  function rebuild() {
    countValue.textContent = String(strings.length);
    removeButton.disabled = strings.length <= MIN_STRINGS;
    addButton.disabled = strings.length >= MAX_STRINGS;
    saveButton.disabled = strings.length === 0;
    for (const button of familyPicker.querySelectorAll('.chip')) {
      button.setAttribute(
        'aria-pressed',
        String(app.families[[...familyPicker.querySelectorAll('.chip')].indexOf(button)].name === family),
      );
    }
    stringList.replaceChildren(...strings.map(stringRow));
  }

  // ---- Note picker -----------------------------------------------------

  const dialog = el('dialog', { class: 'note-picker' });

  function openPicker(index) {
    let pitchClass = ((strings[index] % 12) + 12) % 12;
    let octave = core.noteOctave(strings[index]);

    const preview = el('p', { class: 'body-medium', style: 'color:var(--primary)' });
    const pitchRow = el('div', { class: 'chip-row', role: 'group', 'aria-label': 'Note' });
    const octaveRow = el('div', { class: 'chip-row', role: 'group', 'aria-label': 'Octave' });
    const setButton = el('button', {
      class: 'button button--text',
      type: 'button',
      text: 'Set',
      onClick: () => {
        strings = strings.map((value, i) => (i === index ? midiOf() : value));
        dialog.close();
        rebuild();
      },
    });

    const midiOf = () => (octave + 1) * 12 + pitchClass;

    function refresh() {
      const midi = midiOf();
      const valid = midi >= core.minMidi && midi <= core.maxMidi;
      setButton.disabled = !valid;
      preview.textContent = valid
        ? `${core.noteName(midi, settings.useFlats)} · ${core.noteFrequency(midi, settings.referencePitchHz).toFixed(2)} Hz`
        : '';
      pitchRow.replaceChildren(
        ...Array.from({ length: 12 }, (_, value) =>
          el('button', {
            class: 'chip',
            type: 'button',
            text: core.notePitchClass(value, settings.useFlats),
            'aria-pressed': String(value === pitchClass),
            onClick: () => { pitchClass = value; refresh(); },
          }),
        ),
      );
      octaveRow.replaceChildren(
        ...Array.from({ length: 9 }, (_, value) => {
          const candidate = (value + 1) * 12 + pitchClass;
          return el('button', {
            class: 'chip',
            type: 'button',
            text: String(value),
            'aria-pressed': String(value === octave),
            disabled: candidate < core.minMidi || candidate > core.maxMidi,
            onClick: () => { octave = value; refresh(); },
          });
        }),
      );
    }

    refresh();
    dialog.replaceChildren(
      el('h2', { text: 'Choose a note' }),
      pitchRow,
      el('p', { class: 'label-medium', style: 'margin:12px 0 4px', text: 'Octave' }),
      octaveRow,
      preview,
      el('div', { class: 'dialog__actions' }, [
        el('button', {
          class: 'button button--text',
          type: 'button',
          text: 'Cancel',
          onClick: () => dialog.close(),
        }),
        setButton,
      ]),
    );
    dialog.showModal();
  }

  const saveButton = el('button', {
    class: 'button button--text',
    type: 'button',
    text: 'Save',
    onClick: save,
  });

  rebuild();

  const node = el('div', { class: 'screen' }, [
    appBar({
      title: { text: existing ? 'Edit tuning' : 'New tuning' },
      leading: iconButton('arrowBack', 'Discard and go back', () => app.navigate('#/library')),
      actions: [saveButton],
    }),
    el('div', { class: 'scroll' }, [
      el('div', { class: 'pane editor' }, [
        nameField,
        familyPicker,
        countRow,
        stringList,
        el('p', {
          class: 'body-small',
          text:
            'Strings are listed the way you count them on the instrument — the highest ' +
            'number first. Re-entrant tunings are fine; the notes do not have to ascend.',
        }),
      ]),
    ]),
    dialog,
  ]);

  return { node, update: () => {} };
}
