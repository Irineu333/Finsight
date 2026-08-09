package com.neoutils.finsight.ui.model

import com.neoutils.finsight.extension.DisplayAmount
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether the account card shows the yield line.
 *
 * The declaration alone is not the criterion, and that is the whole point: the ledger
 * separates yield from income as soon as the dimension exists, whatever an account
 * declares now. A month that already holds a yield must keep showing it, or the money
 * would be in neither line and the column would stop adding up.
 */
class AccountUiYieldLineTest {

    private val zero = DisplayAmount.natural(0.0, "BRL", isApproximate = false)

    private fun accountUi(yieldsInterest: Boolean, yield: Double) = AccountUi(
        id = 1,
        openingBalance = zero,
        balance = zero,
        income = zero,
        yield = DisplayAmount.forcedPositive(yield, "BRL", isApproximate = false),
        expense = zero,
        adjustment = zero,
        settlement = zero,
        yieldsInterest = yieldsInterest,
    )

    @Test
    fun `a declared account shows the line even before its first yield`() {
        // Otherwise the first launch would have nowhere to be tapped from.
        assertTrue(accountUi(yieldsInterest = true, yield = 0.0).showsYield)
    }

    @Test
    fun `a withdrawn declaration keeps the line while the period holds a yield`() {
        assertTrue(accountUi(yieldsInterest = false, yield = 12.40).showsYield)
    }

    @Test
    fun `no declaration and no yield is no line`() {
        assertFalse(accountUi(yieldsInterest = false, yield = 0.0).showsYield)
    }
}
