/** Small DOM helpers, and the icons the Android app draws with. */

/**
 * Builds an element. Attributes starting with `on` become listeners, `class`,
 * `html` and `text` are handled specially, everything else is an attribute.
 */
export function el(tag, attributes = {}, children = []) {
  const node = document.createElement(tag);
  for (const [name, value] of Object.entries(attributes)) {
    if (value === null || value === undefined || value === false) continue;
    if (name === 'class') node.className = value;
    else if (name === 'text') node.textContent = value;
    else if (name === 'html') node.innerHTML = value;
    else if (name.startsWith('on')) node.addEventListener(name.slice(2).toLowerCase(), value);
    else if (value === true) node.setAttribute(name, '');
    else node.setAttribute(name, value);
  }
  for (const child of [].concat(children)) {
    if (child === null || child === undefined || child === false) continue;
    node.append(child);
  }
  return node;
}

/**
 * Material Symbols, the same glyphs androidx.compose.material.icons draws.
 * Path data from the Material Symbols set, Apache License 2.0.
 */
const PATHS = {
  star: 'M12 17.27 18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z',
  starBorder:
    'M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 ' +
    '9.24zM12 15.4l-3.76 2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1l1.71 4.04 4.38.38-3.32 2.88 1 ' +
    '4.28L12 15.4z',
  settings:
    'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92' +
    '-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-' +
    '.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 ' +
    '0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 ' +
    '1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 ' +
    '2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96' +
    'c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-' +
    '3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
  library:
    'M20 2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 4h-3v5.5c0 ' +
    '1.38-1.12 2.5-2.5 2.5S10 12.88 10 11.5s1.12-2.5 2.5-2.5c.57 0 1.08.19 1.5.51V4h4v2zM4 6H2v14' +
    'c0 1.1.9 2 2 2h14v-2H4V6z',
  arrowBack: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z',
  add: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
  remove: 'M19 13H5v-2h14v2z',
  search:
    'M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 ' +
    '16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99' +
    ' 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
  close: 'M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 ' +
    '17.59 13.41 12z',
  delete: 'M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z',
  edit:
    'M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34' +
    'c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z',
  moreVert:
    'M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-' +
    '2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z',
  expandMore: 'M16.59 8.59 12 13.17 7.41 8.59 6 10l6 6 6-6z',
  check: 'M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z',
  copy:
    'M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 ' +
    '2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z',
  arrowUp: 'M7.41 15.41 12 10.83l4.59 4.58L18 14l-6-6-6 6z',
  arrowDown: 'M7.41 8.59 12 13.17l4.59-4.58L18 10l-6 6-6-6z',
};

/** An inline `<span class="icon">` holding one glyph, tinted by `currentColor`. */
export function icon(name, extraClass = '') {
  return el('span', {
    class: `icon ${extraClass}`.trim(),
    html: `<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="${PATHS[name]}"/></svg>`,
  });
}

/** An icon button with an accessible name. */
export function iconButton(name, label, onClick, options = {}) {
  return el(
    'button',
    {
      class: `icon-button ${options.class || ''}`.trim(),
      type: 'button',
      'aria-label': label,
      title: label,
      disabled: options.disabled,
      onClick,
    },
    [icon(name)],
  );
}

/** Wraps raw SVG markup, such as an instrument glyph, in an icon span. */
export function svgIcon(markup, extraClass = '') {
  return el('span', { class: `icon ${extraClass}`.trim(), html: markup });
}

/** A standard top app bar: optional back button, title block, trailing actions. */
export function appBar({ title, subtitle, leading, actions = [] }) {
  return el('header', { class: 'app-bar' }, [
    leading,
    el('div', { class: 'app-bar__title' }, [
      ...(title.icon ? [title.icon] : []),
      el('div', { class: 'app-bar__text' }, [
        el('div', { class: 'title-large', text: title.text }),
        subtitle ? el('div', { class: 'app-bar__subtitle label-medium', text: subtitle }) : null,
      ]),
    ]),
    ...actions,
  ]);
}

/** A two-or-more-way segmented choice, as Material's SingleChoiceSegmentedButtonRow. */
export function segmented(options, selected, onSelect, label) {
  return el(
    'div',
    { class: 'segmented', role: 'group', 'aria-label': label },
    options.map((option) =>
      el('button', {
        type: 'button',
        text: option.label,
        'aria-pressed': String(option.value === selected),
        onClick: () => onSelect(option.value),
      }),
    ),
  );
}

/** Formats a frequency the way the Android readout does. */
export function hz(value, decimals = 1) {
  return value === null || value === undefined ? '— Hz' : `${value.toFixed(decimals)} Hz`;
}
