/**
 * Chops the microphone stream into the fixed-size hops the detector wants.
 *
 * The browser hands audio over in 128-sample render quanta, while the analysis
 * wants 2048 at a time. Doing the regrouping on the audio thread means the main
 * thread is woken about 23 times a second rather than 375, and each wake-up
 * already carries a whole hop.
 *
 * Only the regrouping happens here. The analysis itself stays on the main
 * thread: a hop costs around a millisecond to process and arrives every 43, so
 * there is no need to put an FFT on a thread that must never miss a deadline.
 */
class HopProcessor extends AudioWorkletProcessor {
  constructor(options) {
    super();
    this.hopSize = options.processorOptions.hopSize;
    this.buffer = new Float32Array(this.hopSize);
    this.filled = 0;
  }

  process(inputs) {
    const channel = inputs[0] && inputs[0][0];
    // A disconnected or not-yet-flowing stream gives no channel at all. Stay
    // alive and wait for it rather than letting the node be collected.
    if (!channel) return true;

    let offset = 0;
    while (offset < channel.length) {
      const take = Math.min(channel.length - offset, this.hopSize - this.filled);
      this.buffer.set(channel.subarray(offset, offset + take), this.filled);
      this.filled += take;
      offset += take;

      if (this.filled === this.hopSize) {
        // Transfer a copy rather than the working buffer, which is reused.
        const hop = this.buffer.slice();
        this.port.postMessage(hop, [hop.buffer]);
        this.filled = 0;
      }
    }
    return true;
  }
}

registerProcessor('hop-processor', HopProcessor);
