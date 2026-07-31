package com.neoutils.finsight.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A rate is typed left to right, and the field must not type it for the user.
 *
 * Two defects live here. The first: a field that filtered for digits and `.` swallowed
 * the comma a pt-BR keyboard emits, so `5,32` became `532` — the dollar registered at
 * five hundred, with the save button enabled. The second was my fix for it: filling from
 * the right, the way money does, which turned `5,32` into `0,0532`. Money fills from the
 * right because its last two digits are always cents. A rate has no such thing.
 */
class RateInputTransformationTest {

    private fun typed(text: String, separator: String = ",") =
        RateInputTransformation(separator).keep(text)

    @Test
    fun `what was typed is what stays`() {
        assertEquals("5", typed("5"))
        assertEquals("5,3", typed("5,3"))
        assertEquals("5,32", typed("5,32"))
        assertEquals("1234,5678", typed("1234,5678"))
    }

    @Test
    fun `the separator the keyboard emits becomes the language's`() {
        assertEquals(typed("5,32"), typed("5.32"))
        assertEquals("5.32", typed("5,32", separator = "."))
    }

    @Test
    fun `only one separator survives, and it is the first`() {
        assertEquals("5,32", typed("5,3,2"))
        assertEquals("5,32", typed("5.3.2"))
    }

    @Test
    fun `a leading separator gets its zero`() {
        assertEquals("0,5", typed(",5"))
        assertEquals("0,5", typed(".5"))
    }

    /** The scale the rates screen shows; past it the digits are dropped, never rounded. */
    @Test
    fun `no more decimals than a rate is shown with`() {
        assertEquals("5,4321", typed("5,43219999"))
    }

    @Test
    fun `anything that is not a rate does not survive`() {
        assertEquals("", typed(""))
        assertEquals("", typed("abc"))
        assertEquals("532", typed("5R$3 2"))
    }
}
