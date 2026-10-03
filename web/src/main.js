/**
 * The web app shell: holds the state, routes between screens, and owns the
 * microphone's lifetime.
 *
 * The tuning itself is not implemented here. Pitch detection, the note maths
 * and the catalog all come from the Kotlin core in `core/`, compiled to
 * JavaScript, so the browser and the Android app agree by construction rather
 * than by two implementations being kept in step by hand.
 */

import * as core from '../vendor/NobsTuner-core.mjs';
import { Store } from './store.js';
import { Microphone, MicState } from './audio.js';
import { el } from './ui.js';
import { APP_VERSION } from './version.js';
import { tunerView } from './views/tuner.js';
import { libraryView } from './views/library.js';
import { editorView } from './views/editor.js';
import { settingsView } from './views/settings.js';

const root = document.getElementById('app');
const liveRegion = document.getElementById('live-reading');

const defaults = JSON.parse(core.defaultsJson());
const presets = JSON.parse(core.presetsJson());
const families = JSON.parse(core.familiesJson());
const displayStyles = JSON.parse(core.displayStylesJson());

const store = new Store(defaults);

const state = {
  ...store.state,
  reading: null,
  manualStringIndex: null,
  tunedStrings: new Set(),
  micState: MicState.IDLE,
  micMessage: null,
};

let currentView = null;
let currentRoute = null;
let wakeLock = null;
let installPrompt = null;
let lastAnnounced = { text: '', at: 0 };

// ---- Derived data --------------------------------------------------------

/** Adds the two spellings a tuning is shown with, in the current accidental. */
function withSummaries(tuning) {
  const useFlats = state.settings.useFlats;
  return {
    ...tuning,
    summary: tuning.strings.map((midi) => core.notePitchClass(midi, useFlats)).join(' '),
    detailedSummary: tuning.strings.map((midi) => core.noteName(midi, useFlats)).join(' '),
  };
}

function allTunings() {
  return [...presets, ...state.customTunings].map(withSummaries);
}

function findTuning(id) {
  const found = presets.find((t) => t.id === id) || state.customTunings.find((t) => t.id === id);
  return found ? withSummaries(found) : null;
}

function currentTuning() {
  return findTuning(state.selectedTuningId) || withSummaries(presets[0]);
}

/**
 * What the window looks like now, in the terms the tuner screen cares about.
 * The thresholds are WindowShape's, from ui/components/Responsive.kt.
 */
function windowShape() {
  const width = window.innerWidth;
  const height = window.innerHeight;
  return {
    width,
    height,
    isCompactWidth: width < 600,
    isShort: height < 520,
    isTightHeight: height < 700,
    prefersSideBySide: (width > height && height < 520) || width >= 840,
    readingMaxWidth: width < 600 ? 440 : 460,
  };
}

// ---- The object the views talk to ---------------------------------------

const app = {
  core,
  state,
  defaults,
  families,
  displayStyles,
  version: APP_VERSION,
  get settings() { return state.settings; },
  get presets() { return presets.map(withSummaries); },
  get tuning() { return currentTuning(); },
  get shape() { return windowShape(); },
  get readingWidth() {
    const shape = windowShape();
    const columns = shape.prefersSideBySide ? 2 : 1;
    const gutters = shape.prefersSideBySide ? 40 + 24 : 32;
    return Math.min(shape.readingMaxWidth, (shape.width - gutters) / columns);
  },
  get installPrompt() { return installPrompt; },

  allTunings,
  findTuning,
  familyName: (name) => families.find((f) => f.name === name).displayName,
  familySeed: (name) => families.find((f) => f.name === name).seedStrings,
  isFavorite: (id) => state.favoriteIds.includes(id),

  navigate(hash) {
    location.hash = hash;
  },

  setChromaticMode(enabled) {
    store.update({ chromaticMode: enabled });
    Object.assign(state, { chromaticMode: enabled });
    clearTarget();
    render();
  },

  selectTuning(id) {
    store.update({ selectedTuningId: id, chromaticMode: false });
    Object.assign(state, { selectedTuningId: id, chromaticMode: false });
    clearTarget();
    app.navigate('#/');
  },

  toggleFavorite(id) {
    const favorites = state.favoriteIds.includes(id)
      ? state.favoriteIds.filter((value) => value !== id)
      : [...state.favoriteIds, id];
    store.update({ favoriteIds: favorites });
    state.favoriteIds = favorites;
    if (currentRoute === 'tuner') render();
  },

  toggleFamily(name) {
    const expanded = state.expandedFamilies.includes(name)
      ? state.expandedFamilies.filter((value) => value !== name)
      : [...state.expandedFamilies, name];
    store.update({ expandedFamilies: expanded });
    state.expandedFamilies = expanded;
  },

  /** Pass null to go back to automatic string detection. */
  selectString(index) {
    state.manualStringIndex = index;
    currentView?.update?.();
  },

  saveCustomTuning({ existingId, name, family, strings }) {
    const tuning = {
      id: existingId || `custom_${crypto.randomUUID()}`,
      name: name.trim() || 'Untitled tuning',
      family,
      strings,
      isCustom: true,
    };
    const others = state.customTunings.filter((t) => t.id !== tuning.id);
    const updated = [...others, tuning].sort((a, b) =>
      a.name.toLowerCase().localeCompare(b.name.toLowerCase()),
    );
    store.update({ customTunings: updated, selectedTuningId: tuning.id, chromaticMode: false });
    Object.assign(state, {
      customTunings: updated,
      selectedTuningId: tuning.id,
      chromaticMode: false,
    });
    clearTarget();
    // Straight back to the tuner: saving selects the tuning, and the library in
    // between would just be a flash.
    app.navigate('#/');
  },

  deleteCustomTuning(id) {
    const remaining = state.customTunings.filter((t) => t.id !== id);
    const favorites = state.favoriteIds.filter((value) => value !== id);
    // Selecting a deleted tuning would leave the tuner with nothing to aim at.
    const selected =
      state.selectedTuningId === id ? defaults.defaultTuningId : state.selectedTuningId;
    store.update({
      customTunings: remaining,
      favoriteIds: favorites,
      selectedTuningId: selected,
    });
    Object.assign(state, {
      customTunings: remaining,
      favoriteIds: favorites,
      selectedTuningId: selected,
    });
  },

  setSetting(key, value, options = {}) {
    store.updateSettings({ [key]: value });
    state.settings[key] = value;

    if (key === 'themeMode') applyTheme();
    if (key === 'keepScreenOn') updateWakeLock();
    if (key === 'autoDetectString' && value) state.manualStringIndex = null;
    if (options.rerender) render();
  },

  install() {
    installPrompt?.prompt();
    installPrompt = null;
  },

  startListening() {
    microphone.start();
  },
};

// ---- Microphone ----------------------------------------------------------

const microphone = new Microphone(core, onPitch, onMicState);

function onMicState(micState, message) {
  state.micState = micState;
  state.micMessage = message;
  if (micState !== MicState.LISTENING) {
    state.reading = null;
    currentView?.update?.();
  }
  currentView?.onNotice?.();
  updateWakeLock();
}

function onPitch(pitch) {
  if (!pitch) {
    if (state.reading !== null) {
      state.reading = null;
      currentView?.update?.();
    }
    return;
  }

  const strings = state.chromaticMode ? new Int32Array(0) : Int32Array.from(currentTuning().strings);
  const manual =
    state.settings.autoDetectString || state.manualStringIndex === null
      ? state.manualStringIndex ?? -1
      : state.manualStringIndex;

  state.reading = core.resolve(
    pitch,
    strings,
    state.settings.referencePitchHz,
    state.settings.toleranceCents,
    manual,
  );

  // Strings the player has already brought into tune this session.
  if (state.reading.inTune && state.reading.stringIndex >= 0) {
    state.tunedStrings.add(state.reading.stringIndex);
  }

  currentView?.update?.();
  announce();
}

/** Clears the "already tuned" ticks whenever the target changes. */
function clearTarget() {
  state.tunedStrings.clear();
  state.manualStringIndex = null;
  state.reading = null;
  microphone.reset();
}

/**
 * Speaks the reading for screen readers. Rate-limited, because a live region
 * updated twenty times a second is unusable.
 */
function announce() {
  const reading = state.reading;
  if (!reading) return;
  const name = core.noteName(reading.targetMidi, state.settings.useFlats);
  const text = reading.inTune
    ? `${name}, in tune`
    : `${name}, ${Math.abs(Math.round(reading.cents))} cents ${reading.cents < 0 ? 'flat' : 'sharp'}`;
  const now = performance.now();
  if (text === lastAnnounced.text || now - lastAnnounced.at < 1500) return;
  lastAnnounced = { text, at: now };
  liveRegion.textContent = text;
}

// ---- Theme and wake lock -------------------------------------------------

function applyTheme() {
  const mode = state.settings.themeMode;
  document.documentElement.dataset.theme = mode.toLowerCase();
  const dark =
    mode === 'DARK' ||
    (mode === 'SYSTEM' && window.matchMedia('(prefers-color-scheme: dark)').matches);
  document
    .querySelector('meta[name="theme-color"]')
    .setAttribute('content', dark ? '#15130E' : '#FFFBF2');
  currentView?.onThemeChange?.();
}

/** Keeps the screen awake while the tuner is listening, if the user asked. */
async function updateWakeLock() {
  const wanted =
    state.settings.keepScreenOn && microphone.listening && document.visibilityState === 'visible';
  if (wanted && !wakeLock) {
    try {
      wakeLock = await navigator.wakeLock?.request('screen');
      wakeLock?.addEventListener('release', () => { wakeLock = null; });
    } catch {
      // Denied, unsupported, or the battery saver says no. Not worth a message.
    }
  } else if (!wanted && wakeLock) {
    await wakeLock.release().catch(() => {});
    wakeLock = null;
  }
}

// ---- Routing -------------------------------------------------------------

function parseRoute() {
  const hash = location.hash.replace(/^#\/?/, '');
  const [path, search] = hash.split('?');
  const params = new URLSearchParams(search || '');
  if (path === 'library') return { name: 'library' };
  if (path === 'settings') return { name: 'settings' };
  if (path === 'editor') {
    return { name: 'editor', editId: params.get('edit'), seedId: params.get('seed') };
  }
  return { name: 'tuner' };
}

function render() {
  const route = parseRoute();
  currentView?.destroy?.();

  if (route.name === 'library') currentView = libraryView(app);
  else if (route.name === 'settings') currentView = settingsView(app);
  else if (route.name === 'editor') currentView = editorView(app, route);
  else currentView = tunerView(app);

  currentRoute = route.name;
  root.replaceChildren(currentView.node);
  root.removeAttribute('aria-busy');

  // The microphone is held only while the tuner is actually in front of the
  // user, the way the Android screen holds it only while resumed.
  if (route.name === 'tuner') startIfAllowed();
  else microphone.stop();
}

/**
 * Opens the microphone without a prompt where the browser already says it is
 * allowed. On a first visit the tuner shows the "Grant access" notice instead,
 * because asking for a microphone before the user has seen the page is both
 * rude and, under most autoplay policies, futile.
 */
async function startIfAllowed() {
  if (microphone.listening) return;
  if (state.micState === MicState.DENIED || state.micState === MicState.UNAVAILABLE) return;
  if (await Microphone.permissionGranted()) microphone.start();
  else if (state.micState === MicState.IDLE) {
    onMicState(MicState.DENIED, 'Nobs Tuner needs the microphone to hear your instrument.');
  }
}

// ---- Wiring --------------------------------------------------------------

window.addEventListener('hashchange', render);

window.addEventListener('resize', debounce(() => {
  if (currentRoute === 'tuner') render();
}, 150));

document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'hidden') microphone.stop();
  else if (currentRoute === 'tuner') startIfAllowed();
  updateWakeLock();
});

window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', applyTheme);

window.addEventListener('beforeinstallprompt', (event) => {
  event.preventDefault();
  installPrompt = event;
  if (currentRoute === 'settings') render();
});

function debounce(fn, delay) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delay);
  };
}

applyTheme();
render();

// ---- Service worker ------------------------------------------------------

if ('serviceWorker' in navigator) {
  window.addEventListener('load', async () => {
    try {
      const registration = await navigator.serviceWorker.register(
        new URL('../sw.js', import.meta.url),
        { scope: './' },
      );
      registration.addEventListener('updatefound', () => {
        const installing = registration.installing;
        installing?.addEventListener('statechange', () => {
          // A worker that lands in `installed` while one is already in control
          // is a new version waiting for the page to let go.
          if (installing.state === 'installed' && navigator.serviceWorker.controller) {
            showUpdateToast(registration);
          }
        });
      });
    } catch {
      // No service worker means no offline use, which is not worth a message.
    }
  });
}

function showUpdateToast(registration) {
  if (document.querySelector('.toast')) return;
  document.body.append(
    el('div', { class: 'toast', role: 'status' }, [
      el('span', { text: 'A new version is ready.' }),
      el('button', {
        class: 'button button--text',
        type: 'button',
        text: 'Reload',
        onClick: () => {
          registration.waiting?.postMessage('skip-waiting');
          location.reload();
        },
      }),
    ]),
  );
}
