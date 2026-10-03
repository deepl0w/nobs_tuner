/**
 * The four ways of drawing how far off the note is, ported from
 * ui/components/MeterStyles.kt and TuningMeter.kt.
 *
 * All four take the same inputs and occupy the same box, so switching style
 * never reshuffles the rest of the screen. Compose measures in dp; here one dp
 * is drawn as one CSS pixel, which on a phone comes out at very nearly the same
 * physical size.
 */

/** Full-scale deviation shown by every style, in cents either side of centre. */
const RANGE_CENTS = 50;

/** Width-to-height ratio of each style's box. */
const ASPECT = { NEEDLE: 1.85, BAR: 3.4, STROBE: 2.6, DIGITAL: 9 };

const STROBE_PIXELS_PER_CENT_PER_SECOND = 1.6;

const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

/** Colour for a deviation: in tune, nearly there, or well out. */
export function statusFor(cents, toleranceCents) {
  if (cents === null) return 'idle';
  const off = Math.abs(cents);
  if (off <= toleranceCents) return 'inTune';
  if (off <= 15) return 'close';
  return 'off';
}

export function describe(cents, toleranceCents) {
  if (cents === null) return 'No note detected';
  if (Math.abs(cents) <= toleranceCents) return 'In tune';
  return cents < 0
    ? `${Math.round(Math.abs(cents))} cents flat`
    : `${Math.round(cents)} cents sharp`;
}

/**
 * A spring, so the needle feels weighted rather than teleporting.
 *
 * Matches Compose's `spring(dampingRatio, stiffness)` closely enough to look
 * the same: unit mass, integrated in small fixed steps so a dropped frame
 * cannot blow it up.
 */
class Spring {
  constructor(dampingRatio, stiffness) {
    this.dampingRatio = dampingRatio;
    this.stiffness = stiffness;
    this.value = 0;
    this.velocity = 0;
    this.target = 0;
  }

  snapTo(value) {
    this.value = this.target = value;
    this.velocity = 0;
  }

  advance(seconds) {
    if (reduceMotion.matches) {
      this.snapTo(this.target);
      return this.value;
    }
    const damping = 2 * this.dampingRatio * Math.sqrt(this.stiffness);
    let remaining = Math.min(seconds, 0.1);
    while (remaining > 0) {
      const step = Math.min(remaining, 1 / 240);
      const acceleration =
        -this.stiffness * (this.value - this.target) - damping * this.velocity;
      this.velocity += acceleration * step;
      this.value += this.velocity * step;
      remaining -= step;
    }
    return this.value;
  }
}

/** Eases a colour towards its target over roughly 150 ms. */
class ColorFade {
  constructor() {
    this.current = null;
    this.target = null;
  }

  advance(target, seconds) {
    if (!this.current || reduceMotion.matches) {
      this.current = target;
      return this.current;
    }
    if (target !== this.target) {
      this.from = this.current;
      this.target = target;
      this.progress = 0;
    }
    if (this.progress < 1) {
      this.progress = Math.min(1, this.progress + seconds / 0.15);
      this.current = mix(this.from, this.target, this.progress);
    }
    return this.current;
  }
}

function parseColor(value) {
  const probe = document.createElement('canvas').getContext('2d');
  probe.fillStyle = '#000';
  probe.fillStyle = value;
  const hex = probe.fillStyle;
  if (hex.startsWith('#')) {
    return [
      parseInt(hex.slice(1, 3), 16),
      parseInt(hex.slice(3, 5), 16),
      parseInt(hex.slice(5, 7), 16),
    ];
  }
  const parts = hex.match(/[\d.]+/g) || [0, 0, 0];
  return [Number(parts[0]), Number(parts[1]), Number(parts[2])];
}

function mix(from, to, t) {
  const a = parseColor(from);
  const b = parseColor(to);
  const channel = (i) => Math.round(a[i] + (b[i] - a[i]) * t);
  return `rgb(${channel(0)} ${channel(1)} ${channel(2)})`;
}

function withAlpha(color, alpha) {
  const [r, g, b] = parseColor(color);
  return `rgb(${r} ${g} ${b} / ${alpha})`;
}

/**
 * Draws the deviation in whichever idiom the user picked.
 *
 * Owns its own animation loop: the reading arrives about twenty times a second
 * but the needle has to move at the refresh rate in between.
 */
export class Meter {
  /** @param {HTMLElement} container */
  constructor(container) {
    this.container = container;
    this.canvas = document.createElement('canvas');
    this.canvas.className = 'meter';
    this.context = this.canvas.getContext('2d');

    this.style = 'NEEDLE';
    this.toleranceCents = 5;
    this.cents = null;
    this.position = new Spring(0.75, 200);
    this.fade = new ColorFade();
    this.strobePhase = 0;
    this.lastFrame = 0;
    this.frame = 0;
    this.colors = {};

    this.digital = document.createElement('div');
    this.digital.className = 'digital';
    this.digital.innerHTML =
      '<div class="digital__value numeric" aria-hidden="true">–</div>' +
      '<div class="digital__label label-small" aria-hidden="true">CENTS</div>';
    this.digitalValue = this.digital.querySelector('.digital__value');

    this.container.append(this.digital, this.canvas);
    this.container.setAttribute('role', 'img');

    this.resizeObserver = new ResizeObserver(() => this.resize());
    this.resizeObserver.observe(this.container);
    this.applyStyle();
  }

  /** Re-reads the palette; call after the theme changes. */
  readColors() {
    const computed = getComputedStyle(document.documentElement);
    const value = (name) => computed.getPropertyValue(name).trim();
    this.colors = {
      inTune: value('--in-tune'),
      close: value('--close'),
      off: value('--off'),
      idle: value('--idle'),
      track: value('--surface-variant'),
      outline: value('--outline'),
      onSurfaceVariant: value('--on-surface-variant'),
    };
    this.fade.current = null;
  }

  setStyle(style) {
    if (style === this.style) return;
    this.style = style;
    this.applyStyle();
  }

  applyStyle() {
    this.digital.hidden = this.style !== 'DIGITAL';
    this.position.dampingRatio = this.style === 'NEEDLE' ? 0.75 : 1;
    this.resize();
  }

  setTolerance(cents) {
    this.toleranceCents = cents;
  }

  /** Pass null when nothing is being heard. */
  setCents(cents) {
    this.cents = cents;
    this.position.target = Math.max(-RANGE_CENTS, Math.min(RANGE_CENTS, cents ?? 0));
    this.container.setAttribute('aria-label', describe(cents, this.toleranceCents));
    if (this.style === 'DIGITAL') {
      this.digitalValue.textContent =
        cents === null ? '–'
          : Math.abs(cents) < 0.5 ? '0'
            : `${cents > 0 ? '+' : '−'}${Math.round(Math.abs(cents))}`;
      this.digitalValue.style.color =
        cents === null ? this.colors.idle : this.fade.current || this.colors.idle;
    }
  }

  resize() {
    const width = this.container.clientWidth;
    if (!width) return;
    const height = Math.round(width / ASPECT[this.style]);
    const ratio = window.devicePixelRatio || 1;
    this.canvas.style.height = `${height}px`;
    this.canvas.width = Math.round(width * ratio);
    this.canvas.height = Math.round(height * ratio);
    this.context.setTransform(ratio, 0, 0, ratio, 0, 0);
    this.width = width;
    this.height = height;
  }

  start() {
    if (this.frame) return;
    this.readColors();
    this.lastFrame = 0;
    const tick = (now) => {
      this.frame = requestAnimationFrame(tick);
      const seconds = this.lastFrame ? Math.min((now - this.lastFrame) / 1000, 0.1) : 0;
      this.lastFrame = now;
      this.draw(seconds);
    };
    this.frame = requestAnimationFrame(tick);
  }

  stop() {
    cancelAnimationFrame(this.frame);
    this.frame = 0;
  }

  draw(seconds) {
    if (!this.width) this.resize();
    if (!this.width) return;

    const value = this.position.advance(seconds);
    const status = statusFor(this.cents, this.toleranceCents);
    const color = this.fade.advance(this.colors[status], seconds);

    const context = this.context;
    context.clearRect(0, 0, this.width, this.height);
    if (this.style === 'NEEDLE') this.drawNeedle(context, value, color);
    else if (this.style === 'BAR') this.drawBar(context, value, color);
    else if (this.style === 'STROBE') this.drawStrobe(context, color, seconds);
    else this.drawDigital(context, color);
  }

  // ---- Needle -----------------------------------------------------------

  drawNeedle(context, needleCents, color) {
    const { width, height, colors } = this;
    const active = this.cents !== null;
    const stroke = 14;
    const padding = stroke / 2 + 6;
    const radius = Math.min(width / 2 - padding, height - padding);
    const cx = width / 2;
    const cy = height - padding / 2;

    const arc = (from, to, lineWidth, strokeStyle, round) => {
      context.beginPath();
      context.lineWidth = lineWidth;
      context.strokeStyle = strokeStyle;
      context.lineCap = round ? 'round' : 'butt';
      context.arc(cx, cy, radius, from, to);
      context.stroke();
    };

    // Track: a half circle opening upwards.
    arc(Math.PI, 2 * Math.PI, stroke, colors.track, true);

    // The acceptance band, drawn symmetrically about straight up.
    const band = (this.toleranceCents / RANGE_CENTS) * (Math.PI / 2);
    arc(
      -Math.PI / 2 - band,
      -Math.PI / 2 + band,
      stroke,
      active ? colors.inTune : withAlpha(colors.inTune, 0.35),
      false,
    );

    // Ticks every 10 cents, longer at the quarter points.
    for (let tick = -50; tick <= 50; tick += 10) {
      const major = tick % 25 === 0;
      const angle = (tick / RANGE_CENTS) * (Math.PI / 2);
      const inner = radius - stroke / 2 - (major ? 14 : 8);
      const outer = radius - stroke / 2 - 2;
      context.beginPath();
      context.lineWidth = major ? 3 : 1.5;
      context.strokeStyle = withAlpha(colors.outline, major ? 0.9 : 0.45);
      context.lineCap = 'round';
      context.moveTo(cx + inner * Math.sin(angle), cy - inner * Math.cos(angle));
      context.lineTo(cx + outer * Math.sin(angle), cy - outer * Math.cos(angle));
      context.stroke();
    }

    // Needle, with a short counterweight past the pivot.
    const angle = (needleCents / RANGE_CENTS) * (Math.PI / 2);
    const length = radius - stroke - 10;
    context.beginPath();
    context.lineWidth = 5;
    context.strokeStyle = color;
    context.lineCap = 'round';
    context.moveTo(cx - 10 * Math.sin(angle), cy + 10 * Math.cos(angle));
    context.lineTo(cx + length * Math.sin(angle), cy - length * Math.cos(angle));
    context.stroke();

    context.fillStyle = color;
    context.beginPath();
    context.arc(cx, cy, 11, 0, 2 * Math.PI);
    context.fill();
    context.fillStyle = colors.track;
    context.beginPath();
    context.arc(cx, cy, 5, 0, 2 * Math.PI);
    context.fill();
  }

  // ---- Bar --------------------------------------------------------------

  drawBar(context, position, color) {
    const { width, height, colors } = this;
    const barHeight = 18;
    const centreY = height * 0.56;
    const left = 10;
    const right = width - 10;
    const usable = right - left;
    const middle = (left + right) / 2;

    const line = (fromX, toX, thickness, strokeStyle) => {
      context.beginPath();
      context.lineWidth = thickness;
      context.strokeStyle = strokeStyle;
      context.lineCap = 'round';
      context.moveTo(fromX + thickness / 2, centreY);
      context.lineTo(toX - thickness / 2, centreY);
      context.stroke();
    };

    line(left, right, barHeight, colors.track);

    const halfBand = (this.toleranceCents / RANGE_CENTS) * (usable / 2);
    line(
      middle - halfBand,
      middle + halfBand,
      barHeight,
      this.cents !== null ? colors.inTune : withAlpha(colors.inTune, 0.35),
    );

    for (let tick = -50; tick <= 50; tick += 10) {
      const major = tick % 25 === 0;
      const x = middle + (tick / RANGE_CENTS) * (usable / 2);
      const half = major ? 16 : 11;
      context.beginPath();
      context.lineWidth = major ? 2.5 : 1.5;
      context.strokeStyle = withAlpha(colors.outline, major ? 0.9 : 0.45);
      context.lineCap = 'round';
      context.moveTo(x, centreY - half);
      context.lineTo(x, centreY + half);
      context.stroke();
    }

    const markerX = middle + (position / RANGE_CENTS) * (usable / 2);
    const markerHalf = (barHeight + 18) / 2;
    context.beginPath();
    context.lineWidth = 9;
    context.strokeStyle = color;
    context.lineCap = 'round';
    context.moveTo(markerX, centreY - markerHalf);
    context.lineTo(markerX, centreY + markerHalf);
    context.stroke();
  }

  // ---- Strobe -----------------------------------------------------------

  /**
   * Scrolling stripes, after the strobe discs of mechanical tuners: they drift
   * right when the note is sharp, left when it is flat, and freeze when it is
   * right. The phase is integrated over real time rather than animated towards
   * a target, because the point of a strobe is the rate of drift.
   */
  drawStrobe(context, color, seconds) {
    const { width, height, colors } = this;
    this.strobePhase += (this.cents ?? 0) * STROBE_PIXELS_PER_CENT_PER_SECOND * seconds;

    const rows = 3;
    const gap = 8;
    const rowHeight = (height - gap * (rows - 1)) / rows;
    const widths = [30, 21, 14];
    const stripe = this.cents === null ? withAlpha(color, 0.22) : color;

    widths.forEach((stripeWidth, index) => {
      const top = index * (rowHeight + gap);
      context.fillStyle = colors.track;
      context.fillRect(0, top, width, rowHeight);

      context.save();
      context.beginPath();
      context.rect(0, top, width, rowHeight);
      context.clip();
      context.fillStyle = stripe;

      const period = stripeWidth * 2;
      // Scale the drift by stripe width so the narrow bands beat faster, which
      // is what makes a real strobe readable at fine offsets.
      const offset = (((this.strobePhase * (30 / stripeWidth)) % period) + period) % period;
      for (let x = -period + offset; x < width + period; x += period) {
        context.fillRect(x, top, stripeWidth, rowHeight);
      }
      context.restore();
    });
  }

  // ---- Digital ----------------------------------------------------------

  /** A row of lights under the figure: everything between centre and the reading. */
  drawDigital(context, color) {
    const { width, height, colors } = this;
    this.digitalValue.style.color = this.cents === null ? colors.idle : color;

    const count = 21; // ten either side of centre
    const spacing = 4;
    const cellWidth = (width - spacing * (count - 1)) / count;
    const centreIndex = (count - 1) / 2;
    const position =
      this.cents === null
        ? null
        : Math.round(
          Math.max(-centreIndex, Math.min(centreIndex, (this.cents / RANGE_CENTS) * centreIndex)),
        );

    for (let i = 0; i < count; i++) {
      const delta = i - centreIndex;
      const on =
        position === null ? false
          : position === 0 ? delta === 0
            : position > 0 ? delta >= 1 && delta <= position
              : delta >= position && delta <= -1;
      const isCentre = delta === 0;
      context.fillStyle = on
        ? color
        : isCentre && this.cents !== null && Math.abs(this.cents) <= this.toleranceCents
          ? colors.inTune
          : isCentre
            ? withAlpha(colors.track, 0.9)
            : colors.track;
      context.fillRect(i * (cellWidth + spacing), 0, cellWidth, height);
    }
  }

  destroy() {
    this.stop();
    this.resizeObserver.disconnect();
  }
}
