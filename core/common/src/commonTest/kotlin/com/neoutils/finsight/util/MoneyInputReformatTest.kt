package com.neoutils.finsight.util

import com.neoutils.finsight.extension.TEST_SYMBOLS
import com.neoutils.finsight.extension.currencyFormatterOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **What a filled field does when the currency changes under it.**
 *
 * An `InputTransformation` only runs on input, so re-creating one leaves text the user
 * already typed exactly as it was: pick a dollar account under a filled field and it goes
 * on reading `R$ 100,00` while the submit debits 100 dollars. That is the wrong-legend
 * failure of design D10, reached by standing still, and `reformat` is what the
 * `rememberMoneyInputTransformation` seam calls to close it.
 *
 * The rule it has to keep: **the amount is untouched, only its denomination moves.** A
 * field is digits and a denomination, and re-reading it must not multiply, round or drop
 * anything.
 */
class MoneyInputReformatTest {

    private fun transformation(currency: String) =
        MoneyInputTransformation(currencyFormatterOf(TEST_SYMBOLS), currency)

    @Test
    fun `the amount survives a change of denomination`() {
        val typed = transformation("BRL").reformat("10000")
        val redenominated = transformation("USD").reformat(typed)

        // Same hundred, said in another currency — the digits are what the user entered.
        assertEquals(
            transformation("USD").reformat("10000"),
            redenominated,
            "re-reading a filled field changed the amount, not just its denomination",
        )
        assertEquals("10000", redenominated.filter { it.isDigit() })
    }

    @Test
    fun `a negative field stays negative`() {
        val redenominated = transformation("USD").reformat(transformation("BRL").reformat("-2550"))

        assertEquals("2550", redenominated.filter { it.isDigit() })
        assertEquals(true, redenominated.startsWith("-"), "the sign was dropped on the way")
    }

    @Test
    fun `a field with no digits reads as empty`() {
        assertEquals("", transformation("BRL").reformat(""))
        assertEquals("", transformation("BRL").reformat("R$"))
    }
}
