/** The tuner itself: a dial, a readout and a row of strings. */

import { el, icon, iconButton, appBar, segmented, hz } from '../ui.js';
import { INSTRUMENT_ICONS, CHROMATIC_ICON } from '../instrument-icons.js';
import { Meter } from '../meters.js';
import { MicState } from '../audio.js';
import { svgIcon } from '../ui.js';

/**
 * Splits [count] chips into rows of as equal a length as possible, given that
 * at most [maxPerRow] fit side by side.
 *
 * Filling each row to the brim leaves an orphan — six strings in rows of five
 * reads as "5 and 1" rather than as one instrument. Balancing gives 3 + 3, or
 * 2 + 2 + 2 when the window is narrower. Longer rows come first, so seven
 * strings go 4 + 3. Ported from TunerDisplay.kt's `balancedRows`.
 */
export function balancedRows(count, maxPerRow) {
  if (count <= 0) return [];
  const perRow = Math.max(1, maxPerRow);
  const rows = Math.ceil(count / perRow);
  const base = Math.floor(count / rows);
  const remainder = count % rows;
  return Array.from({ length: rows }, (_, index) => base + (index < remainder ? 1 : 0));
}

/**
 * Widest the dial may be before it stops fitting the height it has.
 *
 * Beside the controls rather than above them, the limit comes from the height,
 * not the width: each style has its own width-to-height ratio, and the readout
 * underneath needs its own room. Ported from TunerScreen.kt's `meterWidthFor`.
 */
function meterWidthFor(shape, style) {
  const READOUT_SPACE = 150;
  const ratio = { NEEDLE: 1.85, BAR: 3.4, STROBE: 2.6, DIGITAL: 2.2 }[style];
  const fromHeight = (shape.height - READOUT_SPACE) * ratio;
  return Math.max(160, Math.min(shape.readingMaxWidth, fromHeight));
}

export function tunerView(app) {
  const { core, state, settings, tuning, shape } = app;
  const chromatic = state.chromaticMode;

  // ---- Readout ---------------------------------------------------------

  const flat = el('div', { class: 'readout__arrow', text: '♭', 'aria-hidden': 'true' });
  const sharp = el('div', { class: 'readout__arrow', text: '♯', 'aria-hidden': 'true' });
  const noteName = el('div', { class: 'readout__note' });
  const noteOctave = el('div', { class: 'readout__octave' });
  const status = el('div', { class: 'readout__status' });
  const heardHz = el('div', { class: 'readout__chip-value numeric' });
  const targetHz = el('div', { class: 'readout__chip-value numeric' });

  const readout = el(
    'div',
    { class: `readout${shape.isTightHeight ? ' readout--compact' : ''}` },
    [
      el('div', { class: 'readout__note-row' }, [flat, noteName, noteOctave, sharp]),
      status,
      el('div', { class: 'readout__frequencies' }, [
        el('div', { class: 'readout__chip' }, [
          el('div', { class: 'readout__chip-label label-small', text: 'HEARD' }),
          heardHz,
        ]),
        el('div', { class: 'readout__chip' }, [
          el('div', { class: 'readout__chip-label label-small', text: 'TARGET' }),
          targetHz,
        ]),
      ]),
    ],
  );

  // ---- Meter -----------------------------------------------------------

  const meterBox = el('div', { class: 'meter-box' });
  if (shape.prefersSideBySide) {
    meterBox.style.maxWidth = `${meterWidthFor(shape, settings.displayStyle)}px`;
  }
  const meter = new Meter(meterBox);
  meter.setStyle(settings.displayStyle);
  meter.setTolerance(settings.toleranceCents);

  // ---- Strings ---------------------------------------------------------

  const stringsBox = el('div', { class: 'strings' });
  const chips = [];

  function buildStrings() {
    stringsBox.replaceChildren();
    chips.length = 0;
    if (chromatic) return;

    const spacing = 8;
    const minChip = 48; // a comfortable touch target
    const maxChip = 76;
    const available = app.readingWidth;
    const maxPerRow = Math.max(1, Math.floor((available + spacing) / (minChip + spacing)));
    const rows = balancedRows(tuning.strings.length, maxPerRow);
    const widest = Math.max(...rows);
    // One size for every chip, taken from the longest row, so the rows line up
    // as a grid rather than drifting in size.
    const size = Math.min(
      maxChip,
      Math.max(minChip, (available - spacing * (widest - 1)) / widest),
    );

    let index = 0;
    for (const rowLength of rows) {
      const row = el('div', { class: 'strings__row' });
      for (let i = 0; i < rowLength; i++) {
        const stringIndex = index++;
        const midi = tuning.strings[stringIndex];
        const name = core.notePitchClass(midi, settings.useFlats);
        const octave = core.noteOctave(midi);
        const chip = el(
          'button',
          {
            class: 'string-chip',
            type: 'button',
            onClick: () =>
              app.selectString(stringIndex === state.manualStringIndex ? null : stringIndex),
          },
          [
            el('span', { class: 'string-chip__note', text: name }),
            el('span', { class: 'string-chip__octave', text: String(octave) }),
            icon('check', 'string-chip__tick'),
          ],
        );
        // Set through the CSSOM rather than a style attribute, which the
        // page's Content-Security-Policy forbids.
        chip.style.width = chip.style.height = `${size}px`;
        chip.dataset.index = String(stringIndex);
        chip.dataset.label = `String ${tuning.strings.length - stringIndex}, ${name}${octave}`;
        chips.push(chip);
        row.append(chip);
      }
      stringsBox.append(row);
    }
  }

  buildStrings();

  const stringHint = el('p', { class: 'hint' });

  // ---- Status notice ---------------------------------------------------

  const notice = el('div', { class: 'tuner__notice' });

  function buildNotice() {
    notice.replaceChildren();
    const { micState, micMessage } = app.state;
    if (micState === MicState.LISTENING || micState === MicState.IDLE) return;

    const action =
      micState === MicState.DENIED ? 'Grant access'
        : micState === MicState.NEEDS_GESTURE ? 'Start listening'
          : 'Try again';
    notice.append(
      el('div', { class: 'notice', role: 'alert' }, [
        el('p', { class: 'body-medium u-flush', text: micMessage }),
        micState === MicState.UNAVAILABLE
          ? null
          : el('button', { class: 'button', type: 'button', text: action, onClick: app.startListening }),
      ]),
    );
  }

  buildNotice();

  // ---- Mode switch -----------------------------------------------------

  const modeSummary = el('div', {
    class: 'mode-switch__summary body-medium',
    text: chromatic ? 'Any note' : tuning.detailedSummary,
  });

  const modeSwitch = el('div', { class: 'tuner__group mode-switch' }, [
    segmented(
      [
        { value: false, label: 'Tuning' },
        { value: true, label: 'Chromatic' },
      ],
      chromatic,
      app.setChromaticMode,
      'Tuning mode',
    ),
    modeSummary,
  ]);

  // ---- Assembly --------------------------------------------------------

  const column = el('div', { class: 'tuner__column' }, [
    modeSwitch,
    el('div', { class: 'tuner__group' }, [meterBox, readout]),
    el('div', { class: 'tuner__group' }, [stringsBox, stringHint, notice]),
  ]);

  const body = el(
    'div',
    {
      class:
        'tuner' +
        (shape.prefersSideBySide ? ' tuner--side-by-side' : '') +
        (shape.isTightHeight ? ' tuner--tight' : ''),
    },
    shape.prefersSideBySide
      ? [
        el('div', { class: 'tuner__column' }, [el('div', { class: 'tuner__group' }, [meterBox, readout])]),
        el('div', { class: 'tuner__column' }, [
          modeSwitch,
          el('div', { class: 'tuner__group' }, [stringsBox, stringHint, notice]),
        ]),
      ]
      : [column],
  );

  const bar = appBar({
    title: {
      icon: svgIcon(
        chromatic ? CHROMATIC_ICON : INSTRUMENT_ICONS[tuning.family],
        'icon--lg app-bar__instrument',
      ),
      text: chromatic ? 'Chromatic' : tuning.name,
    },
    subtitle: chromatic ? 'Any note' : app.familyName(tuning.family),
    actions: [
      chromatic
        ? null
        : iconButton(
          app.isFavorite(tuning.id) ? 'star' : 'starBorder',
          app.isFavorite(tuning.id)
            ? `Remove ${tuning.name} from favourites`
            : `Add ${tuning.name} to favourites`,
          () => app.toggleFavorite(tuning.id),
          { class: app.isFavorite(tuning.id) ? 'is-on' : '' },
        ),
      iconButton('library', 'Tuning library', () => app.navigate('#/library')),
      iconButton('settings', 'Settings', () => app.navigate('#/settings')),
    ].filter(Boolean),
  });

  const node = el('div', { class: 'screen' }, [bar, body]);

  // ---- Live updates ----------------------------------------------------

  /**
   * Patches only what changes between frames. A full re-render twenty times a
   * second would lose focus, close menus and waste a lot of layout work.
   */
  function update() {
    const reading = app.state.reading;
    const cents = reading ? reading.cents : null;
    const inTolerance = cents !== null && Math.abs(cents) <= settings.toleranceCents;

    meter.setCents(cents);

    noteName.textContent = reading
      ? core.notePitchClass(reading.targetMidi, settings.useFlats)
      : '♪';
    noteName.classList.toggle('is-idle', !reading);
    noteOctave.textContent = reading ? String(core.noteOctave(reading.targetMidi)) : '';
    status.textContent = !reading
      ? 'Play a note'
      : inTolerance
        ? 'In tune'
        : `${cents > 0 ? '+' : '−'}${Math.abs(Math.round(cents))} cents`;

    const color =
      !reading ? 'var(--on-surface-variant)'
        : inTolerance ? 'var(--in-tune)'
          : Math.abs(cents) <= 15 ? 'var(--close)'
            : 'var(--off)';
    noteName.style.color = color;
    noteOctave.style.color = color;
    status.style.color = color;
    flat.style.color = sharp.style.color = color;
    flat.classList.toggle('is-visible', cents !== null && cents < -settings.toleranceCents);
    sharp.classList.toggle('is-visible', cents !== null && cents > settings.toleranceCents);

    heardHz.textContent = hz(reading ? reading.frequencyHz : null);
    targetHz.textContent = hz(
      reading ? core.noteFrequency(reading.targetMidi, settings.referencePitchHz) : null,
    );

    const active = reading ? reading.stringIndex : -1;
    for (const chip of chips) {
      const index = Number(chip.dataset.index);
      const isActive = index === active;
      const isPinned = index === app.state.manualStringIndex;
      const isTuned = app.state.tunedStrings.has(index);
      chip.classList.toggle('is-active', isActive);
      chip.classList.toggle('is-pinned', isPinned);
      chip.classList.toggle('is-tuned', isTuned);
      chip.setAttribute(
        'aria-label',
        chip.dataset.label + (isTuned ? ', tuned' : '') + (isPinned ? ', selected' : ''),
      );
      chip.setAttribute('aria-pressed', String(isPinned));
    }

    stringHint.textContent = chromatic
      ? ''
      : app.state.manualStringIndex !== null
        ? 'Listening for one string. Tap it again for automatic detection.'
        : 'Tap a string to lock onto it.';
  }

  update();
  meter.start();

  return {
    node,
    update,
    onNotice: buildNotice,
    onThemeChange: () => meter.readColors(),
    onResize: buildStrings,
    destroy: () => meter.destroy(),
  };
}
