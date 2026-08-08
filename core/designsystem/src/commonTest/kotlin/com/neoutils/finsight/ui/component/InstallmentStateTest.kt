package com.neoutils.finsight.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The figure the counter shows while the user is still choosing how many instalments
 * to take. The E2E suite used to assert both `2x $480.00` and `3x $320.00` on screen
 * before anything was written — arithmetic, on a device, for a number the ledger had
 * not yet seen. It is checked here instead, and the flow keeps only the one reading
 * that justifies the figures it goes on to assert.
 *
 * This is deliberately *not* the split the ledger performs. `AddInstallmentUseCase`
 * divides in cents and gives the remainder to the last instalment; this says "about
 * this much a month" and is allowed to be a cent off, which is what every store's
 * checkout says too.
 */
class InstallmentStateTest {

    @Test
    fun `an amount that divides gives the same figure whichever way it is split`() {
        assertEquals(480.0, InstallmentState(count = 2, total = 960.0).installment)
        assertEquals(320.0, InstallmentState(count = 3, total = 960.0).installment)
        assertEquals(240.0, InstallmentState(count = 4, total = 960.0).installment)
    }

    @Test
    fun `one instalment is the whole amount`() {
        assertEquals(1_000.0, InstallmentState(count = 1, total = 1_000.0).installment)
    }

    @Test
    fun `an amount that does not divide is an estimate and says so by rounding`() {
        // $333.33… — the label reads $333.33, while the ledger writes 333.33, 333.33
        // and 333.34. The counter is not the source of what is charged.
        val shown = InstallmentState(count = 3, total = 1_000.0).installment

        assertEquals(33_333L, (shown * 100).toLong())
    }
}
