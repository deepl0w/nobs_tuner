/** The tuning library: favourites, your own, and the built-in catalog. */

import { el, icon, iconButton, appBar, svgIcon } from '../ui.js';
import { INSTRUMENT_ICONS } from '../instrument-icons.js';

export function libraryView(app) {
  const { state, settings } = app;
  let query = '';
  let openMenu = null;

  const list = el('div', { class: 'library__list' });

  const search = el('div', { class: 'search' }, [
    icon('search'),
    el('input', {
      type: 'search',
      placeholder: 'Search tunings',
      'aria-label': 'Search tunings',
      value: query,
      onInput: (event) => {
        query = event.target.value;
        clearButton.hidden = query.length === 0;
        renderList();
      },
    }),
  ]);
  const clearButton = iconButton('close', 'Clear search', () => {
    query = '';
    search.querySelector('input').value = '';
    clearButton.hidden = true;
    renderList();
  });
  clearButton.hidden = true;
  search.append(clearButton);

  function matches(tuning) {
    const needle = query.trim().toLowerCase();
    if (!needle) return true;
    return (
      tuning.name.toLowerCase().includes(needle) ||
      app.familyName(tuning.family).toLowerCase().includes(needle) ||
      tuning.detailedSummary.toLowerCase().includes(needle) ||
      tuning.summary.toLowerCase().includes(needle)
    );
  }

  function row(tuning) {
    const isSelected = tuning.id === state.selectedTuningId && !state.chromaticMode;
    const isFavorite = app.isFavorite(tuning.id);

    const menu = el('div', { class: 'menu', role: 'menu', hidden: true }, [
      tuning.isCustom
        ? el('button', { type: 'button', role: 'menuitem', onClick: () => app.navigate(`#/editor?edit=${encodeURIComponent(tuning.id)}`) }, [icon('edit'), 'Edit'])
        : null,
      el(
        'button',
        {
          type: 'button',
          role: 'menuitem',
          onClick: () => app.navigate(`#/editor?seed=${encodeURIComponent(tuning.id)}`),
        },
        [icon('copy'), tuning.isCustom ? 'Duplicate' : 'Copy to my tunings'],
      ),
      tuning.isCustom
        ? el('button', { type: 'button', role: 'menuitem', onClick: () => confirmDelete(tuning) }, [
          icon('delete'), 'Delete',
        ])
        : null,
    ].filter(Boolean));

    const menuButton = iconButton('moreVert', `More options for ${tuning.name}`, (event) => {
      event.stopPropagation();
      const wasOpen = !menu.hidden;
      closeMenu();
      if (!wasOpen) {
        menu.hidden = false;
        menuButton.setAttribute('aria-expanded', 'true');
        openMenu = { menu, button: menuButton };
      }
    });
    menuButton.setAttribute('aria-haspopup', 'menu');
    menuButton.setAttribute('aria-expanded', 'false');

    return el('div', { class: `tuning-row${isSelected ? ' is-selected' : ''}` }, [
      el(
        'button',
        {
          class: 'tuning-row__main',
          type: 'button',
          onClick: () => app.selectTuning(tuning.id),
        },
        [
          el('div', { class: 'u-row' }, [
            svgIcon(INSTRUMENT_ICONS[tuning.family], 'icon--md tuning-row__icon'),
            el('div', { class: 'u-grow' }, [
              el('div', { class: 'tuning-row__name', text: tuning.name }),
              el('div', { class: 'tuning-row__notes body-small', text: tuning.detailedSummary }),
            ]),
          ]),
        ],
      ),
      el('div', { class: 'tuning-row__actions' }, [
        iconButton(
          isFavorite ? 'star' : 'starBorder',
          isFavorite
            ? `Remove ${tuning.name} from favourites`
            : `Add ${tuning.name} to favourites`,
          () => {
            app.toggleFavorite(tuning.id);
            renderList();
          },
          { class: isFavorite ? 'is-on' : '' },
        ),
        el('div', { class: 'menu-anchor' }, [menuButton, menu]),
      ]),
    ]);
  }

  function closeMenu() {
    if (!openMenu) return;
    openMenu.menu.hidden = true;
    openMenu.button.setAttribute('aria-expanded', 'false');
    openMenu = null;
  }

  function sectionHeader(title) {
    return el('div', {}, [
      el('hr', { class: 'divider' }),
      el('h2', { class: 'section-title u-inset', text: title }),
    ]);
  }

  function renderList() {
    closeMenu();
    const searching = query.trim().length > 0;
    const favorites = app.allTunings().filter((t) => app.isFavorite(t.id)).filter(matches);
    const custom = state.customTunings.filter(matches);
    const byFamily = app.families
      .map((family) => ({
        family,
        tunings: app.presets.filter((t) => t.family === family.name).filter(matches),
      }))
      .filter((group) => group.tunings.length > 0);

    const nodes = [];
    if (!favorites.length && !custom.length && !byFamily.length) {
      nodes.push(
        el('div', { class: 'empty' }, [
          el('p', { class: 'body-large', text: `No tunings match “${query}”` }),
          el('button', {
            class: 'button button--text',
            type: 'button',
            text: 'Create a custom tuning',
            onClick: () => app.navigate('#/editor'),
          }),
        ]),
      );
    }

    if (favorites.length) {
      nodes.push(sectionHeader('Favourites'), ...favorites.map(row));
    }
    if (custom.length) {
      nodes.push(sectionHeader('My tunings'), ...custom.map(row));
    }

    for (const { family, tunings } of byFamily) {
      // A search is an explicit request to see what matched, so matching
      // sections open themselves while one is running.
      const expanded = searching || state.expandedFamilies.includes(family.name);
      const header = el(
        'button',
        {
          class: 'family-header',
          type: 'button',
          'aria-expanded': String(expanded),
          onClick: () => {
            app.toggleFamily(family.name);
            renderList();
          },
        },
        [
          svgIcon(INSTRUMENT_ICONS[family.name], 'icon--md'),
          el('span', { class: 'family-header__name', text: family.displayName }),
          el('span', { class: 'family-header__count', text: String(tunings.length) }),
          icon('expandMore', 'family-header__chevron'),
        ],
      );
      nodes.push(el('div', {}, [el('hr', { class: 'divider' }), header]));
      if (expanded) nodes.push(...tunings.map(row));
    }

    list.replaceChildren(...nodes);
  }

  // ---- Delete confirmation ---------------------------------------------

  const dialog = el('dialog', { class: 'confirm-delete' });

  function confirmDelete(tuning) {
    closeMenu();
    dialog.replaceChildren(
      el('h2', { text: `Delete “${tuning.name}”?` }),
      el('p', {
        class: 'body-medium',
        text: 'This custom tuning will be removed from this device. This cannot be undone.',
      }),
      el('div', { class: 'dialog__actions' }, [
        el('button', {
          class: 'button button--text',
          type: 'button',
          text: 'Cancel',
          onClick: () => dialog.close(),
        }),
        el('button', {
          class: 'button button--text',
          type: 'button',
          text: 'Delete',
          onClick: () => {
            app.deleteCustomTuning(tuning.id);
            dialog.close();
            renderList();
          },
        }),
      ]),
    );
    dialog.showModal();
  }

  renderList();

  const node = el('div', { class: 'screen' }, [
    appBar({
      title: { text: 'Tunings' },
      leading: iconButton('arrowBack', 'Back to tuner', () => app.navigate('#/')),
    }),
    el('div', { class: 'scroll' }, [el('div', { class: 'pane library' }, [search, list])]),
    el('button', {
      class: 'fab',
      type: 'button',
      'aria-label': 'Create a custom tuning',
      onClick: () => app.navigate('#/editor'),
    }, [icon('add')]),
    dialog,
  ]);

  node.addEventListener('click', (event) => {
    if (openMenu && !event.target.closest('.menu-anchor')) closeMenu();
  });

  return { node, update: () => {} };
}
