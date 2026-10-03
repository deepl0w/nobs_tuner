package io.github.deeplow.nobstuner.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The dial's needle and its acceptance band have to agree: the band's edge must
 * sit exactly where the needle sits when the note is [toleranceCents] off.
 * If they disagree, the needle rests inside the green band while the readout
 * says the string is still out, and the user trusts the picture.
 */
class MeterGeometryTest {

    /** Cents that a point this many degrees from straight up stands for. */
    private fun centsAt(degrees: Float) = degrees / HALF_SWEEP_DEGREES * RANGE_CENTS

    @Test
    fun `the needle uses the full half circle`() {
        assertEquals(0f, needleDegreesFor(0f), 1e-4f)
        assertEquals(90f, needleDegreesFor(RANGE_CENTS), 1e-4f)
        assertEquals(-90f, needleDegreesFor(-RANGE_CENTS), 1e-4f)
    }

    @Test
    fun `the band edge is exactly where the needle sits at the tolerance`() {
        for (tolerance in 1..15) {
            assertEquals(
                "band and needle disagree at a tolerance of $tolerance cents",
                needleDegreesFor(tolerance.toFloat()),
                toleranceHalfSweepDegrees(tolerance),
                1e-4f,
            )
        }
    }

    @Test
    fun `the band spans the tolerance and nothing more`() {
        // Read the drawn band back out in cents, which is what the user infers
        // from it. A 5-cent tolerance must not paint 10 cents green.
        assertEquals(5.0f, centsAt(toleranceHalfSweepDegrees(5)), 1e-4f)
        assertEquals(1.0f, centsAt(toleranceHalfSweepDegrees(1)), 1e-4f)
        assertEquals(15.0f, centsAt(toleranceHalfSweepDegrees(15)), 1e-4f)
    }

    @Test
    fun `the band never swallows the whole dial`() {
        // Full scale is 50 cents; the widest tolerance the user can pick is 15.
        assertEquals(27f, toleranceHalfSweepDegrees(15), 1e-4f)
    }
}
