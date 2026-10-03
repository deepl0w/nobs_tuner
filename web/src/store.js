/**
 * Everything the user has chosen, kept in localStorage.
 *
 * The browser counterpart of data/TunerRepository.kt. Like it, custom tunings
 * live in one blob rather than a table — there are tens of them at most and
 * they are always read and written whole — and anything unreadable falls back
 * to defaults rather than throwing, because a corrupt store should cost you
 * your settings, not the use of the tuner.
 */

const KEY = 'nobstuner';

const clamp = (value, min, max) => Math.min(max, Math.max(min, value));

export class Store {
  /** @param {object} defaults parsed from the core's `defaultsJson()` */
  constructor(defaults) {
    this.defaults = defaults;
    this.state = this.read();
  }

  read() {
    const fallback = {
      settings: {
        referencePitchHz: this.defaults.referencePitchHz,
        useFlats: this.defaults.useFlats,
        autoDetectString: this.defaults.autoDetectString,
        keepScreenOn: this.defaults.keepScreenOn,
        themeMode: this.defaults.themeMode,
        toleranceCents: this.defaults.toleranceCents,
        displayStyle: this.defaults.displayStyle,
      },
      customTunings: [],
      favoriteIds: [],
      selectedTuningId: this.defaults.defaultTuningId,
      chromaticMode: false,
      expandedFamilies: [],
    };

    let stored;
    try {
      stored = JSON.parse(localStorage.getItem(KEY) || '{}');
    } catch {
      return fallback;
    }
    if (!stored || typeof stored !== 'object') return fallback;

    const settings = { ...fallback.settings, ...(stored.settings || {}) };
    return {
      settings: {
        referencePitchHz: clamp(
          Number(settings.referencePitchHz) || this.defaults.referencePitchHz,
          this.defaults.minReferencePitchHz,
          this.defaults.maxReferencePitchHz,
        ),
        useFlats: Boolean(settings.useFlats),
        autoDetectString: Boolean(settings.autoDetectString),
        keepScreenOn: Boolean(settings.keepScreenOn),
        themeMode: ['SYSTEM', 'LIGHT', 'DARK'].includes(settings.themeMode)
          ? settings.themeMode
          : this.defaults.themeMode,
        toleranceCents: clamp(
          Math.round(Number(settings.toleranceCents)) || this.defaults.toleranceCents,
          this.defaults.minToleranceCents,
          this.defaults.maxToleranceCents,
        ),
        displayStyle: settings.displayStyle || this.defaults.displayStyle,
      },
      customTunings: Array.isArray(stored.customTunings)
        ? stored.customTunings.filter(isTuning).map((t) => ({ ...t, isCustom: true }))
        : [],
      favoriteIds: Array.isArray(stored.favoriteIds) ? stored.favoriteIds.filter(isString) : [],
      selectedTuningId: isString(stored.selectedTuningId)
        ? stored.selectedTuningId
        : this.defaults.defaultTuningId,
      chromaticMode: Boolean(stored.chromaticMode),
      expandedFamilies: Array.isArray(stored.expandedFamilies)
        ? stored.expandedFamilies.filter(isString)
        : [],
    };
  }

  /**
   * Persists the current state.
   *
   * A full disk or Safari's private mode make this throw; losing the write is
   * survivable and the tuner keeps working from memory, so it is swallowed.
   */
  save() {
    try {
      localStorage.setItem(KEY, JSON.stringify(this.state));
    } catch {
      /* Out of quota or storage disabled. */
    }
  }

  update(changes) {
    Object.assign(this.state, changes);
    this.save();
  }

  updateSettings(changes) {
    Object.assign(this.state.settings, changes);
    this.save();
  }
}

function isString(value) {
  return typeof value === 'string';
}

function isTuning(value) {
  return (
    value &&
    typeof value === 'object' &&
    isString(value.id) &&
    isString(value.name) &&
    Array.isArray(value.strings) &&
    value.strings.length > 0 &&
    value.strings.every((midi) => Number.isInteger(midi))
  );
}
