package com.neoutils.finsight.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RateParseTest {

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
