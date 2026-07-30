package com.neoutils.finsight.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The rate field types like the money field, and for the same reason: the digits fill
 * from the right and no separator the keyboard produced ever survives.
 *
 * That last part is the defect this closes. A decimal keyboard in pt-BR emits `,`; a
 * field that filtered for digits and `.` swallowed it, so `5,32` became `532` — the
 * dollar registered at five hundred, with the save button happily enabled.
 */
class RateInputTransformationTest {

    private fun typed(text: String, separator: String = ",") =
        RateInputTransformation(separator).format(text)

    @Test
    fun `digits fill from the right, in the four places a rate is shown with`() {
        assertEquals("0,0005", typed("5"))
        assertEquals("0,0055", typed("55"))
        assertEquals("5,5000", typed("55000"))
        assertEquals("12,3456", typed("123456"))
    }

    @Test
    fun `no separator the keyboard emits survives`() {
        // The comma keyboard and the dot keyboard type the same rate.
        assertEquals(typed("55000"), typed("5,5000"))
        assertEquals(typed("55000"), typed("5.5000"))
        assertEquals("5,3200", typed("5,32,00"))
    }

    @Test
    fun `the language's separator is the one shown`() {
        assertEquals("5.5000", typed("55000", separator = "."))
    }

    @Test
    fun `an empty field stays empty`() {
        assertEquals("", typed(""))
        assertEquals("", typed("abc"))
    }
}
