package io.github.deeplow.nobstuner

import io.github.deeplow.nobstuner.ui.components.balancedRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalancedRowsTest {

    @Test
    fun `everything on one row when it fits`() {
        assertEquals(listOf(6), balancedRows(count = 6, maxPerRow = 6))
        assertEquals(listOf(4), balancedRows(count = 4, maxPerRow = 8))
    }

    @Test
    fun `six strings split evenly rather than leaving an orphan`() {
        // The case that prompted this: five across and one underneath.
        assertEquals(listOf(3, 3), balancedRows(count = 6, maxPerRow = 5))
        assertEquals(listOf(3, 3), balancedRows(count = 6, maxPerRow = 3))
        assertEquals(listOf(2, 2, 2), balancedRows(count = 6, maxPerRow = 2))
    }

    @Test
    fun `odd counts put the longer rows first`() {
        assertEquals(listOf(4, 3), balancedRows(count = 7, maxPerRow = 5))
        assertEquals(listOf(3, 3, 3), balancedRows(count = 9, maxPerRow = 4))
        assertEquals(listOf(4, 4), balancedRows(count = 8, maxPerRow = 5))
        assertEquals(listOf(3, 3, 2), balancedRows(count = 8, maxPerRow = 3))
    }

    @Test
    fun `rows never exceed what fits`() {
        for (count in 1..12) {
            for (perRow in 1..12) {
                val rows = balancedRows(count, perRow)
                assertEquals("count $count perRow $perRow", count, rows.sum())
                assertTrue("row too long for $count/$perRow: $rows", rows.all { it <= perRow })
                // Balanced means no two rows differ by more than one.
                assertTrue("unbalanced $count/$perRow: $rows", rows.max() - rows.min() <= 1)
                // And never more rows than necessary.
                val minimum = (count + perRow - 1) / perRow
                assertEquals("too many rows for $count/$perRow", minimum, rows.size)
            }
        }
    }

    @Test
    fun `degenerate inputs do not crash`() {
        assertEquals(emptyList<Int>(), balancedRows(count = 0, maxPerRow = 4))
        assertEquals(listOf(1, 1, 1), balancedRows(count = 3, maxPerRow = 0))
    }
}
