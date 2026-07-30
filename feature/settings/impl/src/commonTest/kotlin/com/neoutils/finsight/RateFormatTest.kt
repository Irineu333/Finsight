package com.neoutils.finsight

import com.neoutils.finsight.util.formatRate
import com.neoutils.finsight.util.toRateOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RateFormatTest {

    @Test
    fun `a rate is shown with four places and the language's separator`() {
        assertEquals("5,5000", formatRate(5.5, ","))
        assertEquals("5.5000", formatRate(5.5, "."))
        assertEquals("0,1818", formatRate(1.0 / 5.5, ","))
    }

    /**
     * The stored rate is the full quotient and the shown one is rounded — two numbers
     * with two owners (design D11). This is the rounding, and it happens here and
     * nowhere a conversion can read it.
     */
    @Test
    fun `showing rounds and never feeds back`() {
        assertEquals("1,0909", formatRate(1.0909090909, ","))
    }

    @Test
    fun `a typed rate is read with either separator`() {
        assertEquals(5.5, "5,5".toRateOrNull())
        assertEquals(5.5, "5.5".toRateOrNull())
        assertEquals(5.5, " 5.5 ".toRateOrNull())
    }

    /** `null` is what disables the submit button; it is not an error to report. */
    @Test
    fun `what is not a rate reads as nothing`() {
        assertNull("".toRateOrNull())
        assertNull("abc".toRateOrNull())
        assertNull("0".toRateOrNull())
        assertNull("-1".toRateOrNull())
    }
}
