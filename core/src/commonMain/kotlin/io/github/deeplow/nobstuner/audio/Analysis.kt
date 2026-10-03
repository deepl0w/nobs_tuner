package io.github.deeplow.nobstuner.audio

/**
 * The shape of the analysis stream, shared by every platform that feeds the
 * detector.
 *
 * The sample rate is not here: Android opens the microphone at 44.1 kHz, while
 * a browser hands over whatever rate its audio hardware runs at (48 kHz on most
 * machines) and will not be argued with. The frame and hop are what have to
 * agree, because the smoother's release and fold windows are counted in frames.
 */
object Analysis {

    /**
     * 8192 samples (~186 ms at 44.1 kHz). YIN wants a couple of periods inside
     * the integration window, and the lowest note we support — B0 on a 5-string
     * bass at 30.9 Hz — has a period of ~1430 samples. A 4096-sample window only
     * just covers one period and gets unreliable down there.
     */
    const val FRAME_SIZE = 8192

    /** Analysis hop: a new reading every ~46 ms. */
    const val HOP_SIZE = 2048

    /** What Android opens the microphone at, and what the fixtures are resampled to. */
    const val PREFERRED_SAMPLE_RATE = 44_100
}
