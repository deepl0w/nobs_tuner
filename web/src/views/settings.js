/** Everything the user can change. Mirrors ui/screens/SettingsScreen.kt. */

import { el, iconButton, appBar, segmented } from '../ui.js';

export function settingsView(app) {
  const { core, settings, defaults } = app;

  function setting(title, subtitle, control) {
    return el('div', { class: 'setting' }, [
      el('div', { class: 'setting__title', text: title }),
      subtitle ? el('div', { class: 'body-small', text: subtitle }) : null,
      control,
    ]);
  }

  function toggle(title, subtitle, checked, onChange) {
    const id = `toggle-${title.replace(/\W+/g, '-').toLowerCase()}`;
    return el('div', { class: 'setting setting--toggle' }, [
      el('div', { class: 'setting__text' }, [
        el('label', { class: 'setting__title', for: id, text: title }),
        el('div', { class: 'body-small', text: subtitle }),
      ]),
      el('input', {
        id,
        class: 'switch',
        type: 'checkbox',
        checked,
        onChange: (event) => onChange(event.target.checked),
      }),
    ]);
  }

  // ---- Reference pitch -------------------------------------------------

  const pitchValue = el('span', {
    class: 'stepper__value numeric',
    text: `${Math.round(settings.referencePitchHz)} Hz`,
    role: 'status',
  });
  const lower = iconButton('remove', 'Lower reference pitch', () => step(-1), {
    class: 'icon-button--tonal',
  });
  const raise = iconButton('add', 'Raise reference pitch', () => step(1), {
    class: 'icon-button--tonal',
  });
  const reset = el('button', {
    class: 'button button--text',
    type: 'button',
    text: 'Reset',
    onClick: () => setPitch(core.defaultA4Hz),
  });

  function setPitch(hzValue) {
    const clamped = Math.min(
      defaults.maxReferencePitchHz,
      Math.max(defaults.minReferencePitchHz, hzValue),
    );
    app.setSetting('referencePitchHz', clamped);
    pitchValue.textContent = `${Math.round(clamped)} Hz`;
    lower.disabled = clamped <= defaults.minReferencePitchHz;
    raise.disabled = clamped >= defaults.maxReferencePitchHz;
    reset.hidden = clamped === core.defaultA4Hz;
  }

  const step = (delta) => setPitch(app.settings.referencePitchHz + delta);

  const pitchRow = setting(
    'Reference pitch',
    'The frequency of A4 that everything else is measured against.',
    el('div', { class: 'stepper' }, [
      lower,
      pitchValue,
      raise,
      el('span', { style: 'flex:1' }),
      reset,
    ]),
  );

  // ---- Tolerance -------------------------------------------------------

  const toleranceTitle = el('div', { class: 'body-small' });
  const toleranceSlider = el('input', {
    class: 'slider',
    type: 'range',
    min: String(defaults.minToleranceCents),
    max: String(defaults.maxToleranceCents),
    step: '1',
    value: String(settings.toleranceCents),
    'aria-label': 'In-tune tolerance in cents',
    onInput: (event) => {
      const cents = Number(event.target.value);
      app.setSetting('toleranceCents', cents);
      toleranceTitle.textContent = describeTolerance(cents);
    },
  });

  const describeTolerance = (cents) =>
    `How many cents either side still counts as in tune (±${cents}).`;
  toleranceTitle.textContent = describeTolerance(settings.toleranceCents);

  const toleranceRow = el('div', { class: 'setting' }, [
    el('div', { class: 'setting__title', text: 'In-tune tolerance' }),
    toleranceTitle,
    toleranceSlider,
  ]);

  // ---- Display style ---------------------------------------------------

  const styleDescription = el('div', { class: 'body-small' });

  function describeStyle(name) {
    styleDescription.textContent =
      app.displayStyles.find((style) => style.name === name).description;
  }
  describeStyle(settings.displayStyle);

  const styleChips = el(
    'div',
    { class: 'chip-row', role: 'group', 'aria-label': 'Display style' },
    app.displayStyles.map((style) =>
      el('button', {
        class: 'chip',
        type: 'button',
        text: style.displayName,
        'aria-pressed': String(style.name === settings.displayStyle),
        onClick: (event) => {
          app.setSetting('displayStyle', style.name);
          describeStyle(style.name);
          for (const chip of styleChips.children) chip.setAttribute('aria-pressed', 'false');
          event.currentTarget.setAttribute('aria-pressed', 'true');
        },
      }),
    ),
  );

  const node = el('div', { class: 'screen' }, [
    appBar({
      title: { text: 'Settings' },
      leading: iconButton('arrowBack', 'Back to tuner', () => app.navigate('#/')),
    }),
    el('div', { class: 'scroll' }, [
      el('div', { class: 'pane settings' }, [
        el('h2', { class: 'section-title', text: 'Pitch' }),
        pitchRow,
        toleranceRow,
        setting(
          'Accidentals',
          'How notes between the naturals are spelled.',
          segmented(
            [
              { value: false, label: 'Sharps (A♯)' },
              { value: true, label: 'Flats (B♭)' },
            ],
            settings.useFlats,
            (value) => app.setSetting('useFlats', value, { rerender: true }),
            'Accidentals',
          ),
        ),

        el('hr', { class: 'divider', style: 'margin:12px 0' }),
        el('h2', { class: 'section-title', text: 'Tuner' }),
        toggle(
          'Detect string automatically',
          'Aim at whichever string you play instead of selecting one by hand.',
          settings.autoDetectString,
          (value) => app.setSetting('autoDetectString', value),
        ),
        toggle(
          'Keep screen on',
          'Stops the display sleeping while the tuner is open.',
          settings.keepScreenOn,
          (value) => app.setSetting('keepScreenOn', value),
        ),
        el('div', { class: 'setting' }, [
          el('div', { class: 'setting__title', text: 'Display' }),
          styleDescription,
          styleChips,
        ]),

        el('hr', { class: 'divider', style: 'margin:12px 0' }),
        el('h2', { class: 'section-title', text: 'Appearance' }),
        setting(
          'Theme',
          null,
          segmented(
            [
              { value: 'SYSTEM', label: 'System' },
              { value: 'LIGHT', label: 'Light' },
              { value: 'DARK', label: 'Dark' },
            ],
            settings.themeMode,
            (value) => app.setSetting('themeMode', value),
            'Theme',
          ),
        ),

        el('hr', { class: 'divider', style: 'margin:12px 0' }),
        el('h2', { class: 'section-title', text: 'About' }),
        el('p', { class: 'body-medium', style: 'margin:4px 0', text: `Nobs Tuner ${app.version}` }),
        el('p', {
          class: 'body-small',
          text:
            'Audio is analysed entirely in this browser. Nothing is recorded, stored or sent ' +
            'anywhere, and the app works with no network connection at all.',
        }),
        app.installPrompt
          ? el('button', {
            class: 'button',
            type: 'button',
            style: 'margin-top:12px;align-self:flex-start',
            text: 'Install Nobs Tuner',
            onClick: app.install,
          })
          : null,
      ]),
    ]),
  ]);

  setPitch(settings.referencePitchHz);
  return { node, update: () => {} };
}
