/**
 * Microphone capture, the browser's half of what AudioEngine.kt does on
 * Android: open the least-processed input available, cut it into hops, and hand
 * each one to the shared pitch pipeline.
 */

/** Why listening is not currently happening, in terms the UI can act on. */
export const MicState = {
  IDLE: 'idle',
  LISTENING: 'listening',
  NEEDS_GESTURE: 'needs-gesture',
  DENIED: 'denied',
  UNAVAILABLE: 'unavailable',
  ERROR: 'error',
};

/**
 * Browsers default to the processing chain built for voice calls. Automatic
 * gain control and noise suppression both distort a decaying string's pitch and
 * will happily mistake a sustained note for background noise and duck it, which
 * is the same reason the Android build asks for UNPROCESSED. These are requests,
 * not guarantees — a browser is free to ignore them.
 */
const CONSTRAINTS = {
  audio: {
    echoCancellation: false,
    noiseSuppression: false,
    autoGainControl: false,
    channelCount: 1,
  },
  video: false,
};

export class Microphone {
  /**
   * @param {object} core the Kotlin/JS tuner core
   * @param {(pitch: object|null) => void} onPitch called once per analysed hop
   * @param {(state: string, message: string|null) => void} onState
   */
  constructor(core, onPitch, onState) {
    this.core = core;
    this.onPitch = onPitch;
    this.onState = onState;
    this.context = null;
    this.stream = null;
    this.node = null;
    this.source = null;
    this.sink = null;
    this.pipeline = null;
    this.state = MicState.IDLE;
  }

  get listening() {
    return this.state === MicState.LISTENING;
  }

  /** True once the user has granted the microphone, where the browser says so. */
  static async permissionGranted() {
    if (!navigator.permissions?.query) return false;
    try {
      const status = await navigator.permissions.query({ name: 'microphone' });
      return status.state === 'granted';
    } catch {
      // Firefox has no 'microphone' permission descriptor; it throws here.
      return false;
    }
  }

  setState(state, message = null) {
    this.state = state;
    this.onState(state, message);
  }

  async start() {
    if (this.listening) return;

    if (!navigator.mediaDevices?.getUserMedia || !window.AudioWorkletNode) {
      this.setState(
        MicState.UNAVAILABLE,
        window.isSecureContext
          ? 'This browser cannot record audio. Try a recent Chrome, Safari or Firefox.'
          : 'A microphone needs a secure connection. Open this page over HTTPS.',
      );
      return;
    }

    try {
      this.stream = await navigator.mediaDevices.getUserMedia(CONSTRAINTS);
    } catch (error) {
      this.stop();
      if (error.name === 'NotAllowedError' || error.name === 'SecurityError') {
        this.setState(MicState.DENIED, 'Nobs Tuner needs the microphone to hear your instrument.');
      } else if (error.name === 'NotFoundError' || error.name === 'OverconstrainedError') {
        this.setState(MicState.ERROR, 'No microphone was found on this device.');
      } else if (error.name === 'NotReadableError') {
        this.setState(MicState.ERROR, 'The microphone is in use by another app.');
      } else {
        this.setState(MicState.ERROR, error.message || 'The microphone is unavailable right now.');
      }
      return;
    }

    try {
      // No sample rate is requested: a browser will resample to whatever it is
      // told, and resampling the input is a worse starting point than analysing
      // at the rate the hardware actually runs at.
      this.context = new AudioContext();
      await this.context.audioWorklet.addModule(new URL('./hop-worklet.js', import.meta.url));

      this.pipeline = new this.core.TunerPipeline(Math.round(this.context.sampleRate));
      this.node = new AudioWorkletNode(this.context, 'hop-processor', {
        numberOfInputs: 1,
        numberOfOutputs: 1,
        outputChannelCount: [1],
        processorOptions: { hopSize: this.pipeline.hopSize },
      });
      this.node.port.onmessage = (event) => {
        if (!this.pipeline) return;
        this.onPitch(this.pipeline.push(event.data));
      };

      this.source = this.context.createMediaStreamSource(this.stream);
      // An AudioWorkletNode is only pulled while it has a path to the
      // destination, so the graph ends at a silent gain rather than nowhere.
      this.sink = this.context.createGain();
      this.sink.gain.value = 0;
      this.source.connect(this.node).connect(this.sink).connect(this.context.destination);

      if (this.context.state === 'suspended') await this.context.resume();
      if (this.context.state === 'suspended') {
        // Autoplay policy: the page has not had a user gesture it will accept.
        this.setState(MicState.NEEDS_GESTURE, 'Tap to start listening.');
        return;
      }

      this.setState(MicState.LISTENING);
    } catch (error) {
      this.stop();
      this.setState(MicState.ERROR, error.message || 'The microphone could not be opened.');
    }
  }

  /** Releases the microphone. Nothing is retained between sessions. */
  stop() {
    if (this.node) {
      this.node.port.onmessage = null;
      this.node.disconnect();
      this.node = null;
    }
    this.source?.disconnect();
    this.sink?.disconnect();
    this.source = this.sink = null;
    this.stream?.getTracks().forEach((track) => track.stop());
    this.stream = null;
    this.context?.close().catch(() => {});
    this.context = null;
    this.pipeline = null;
    if (this.state === MicState.LISTENING) this.setState(MicState.IDLE);
  }

  /** Forgets the current note without dropping the stream. */
  reset() {
    this.pipeline?.reset();
  }
}
