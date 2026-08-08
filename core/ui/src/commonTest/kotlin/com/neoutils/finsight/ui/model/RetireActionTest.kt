package com.neoutils.finsight.ui.model

import com.neoutils.finsight.extension.DisplayAmount

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The presentation rule, isolated: an account or card with movement is offered as
 * "close", one without as "delete". The *outcome* is not decided here — that is
 * `ArchiveAccountUseCase`'s — so a screen that guesses wrong still gets the right
 * behaviour, just the wrong word for a moment.
 */
class RetireActionTest {

    @Test
    fun `movement makes it a closure and its absence a deletion`() {
        assertEquals(RetireAction.ARCHIVE, retireActionOf(mustPreserve = true))
        assertEquals(RetireAction.DELETE, retireActionOf(mustPreserve = false))
    }

    // The figures are irrelevant to the rule under test; only their presence is.
    private val zero = DisplayAmount.natural(0.0, "BRL", isApproximate = false)

    @Test
    fun `the ui models expose the same rule so the two screens cannot drift`() {
        val moved = AccountUi(
            id = 1,
            openingBalance = zero, balance = zero, income = zero, yield = zero,
            expense = zero, adjustment = zero, settlement = zero,
            hasMovement = true,
        )
        // A non-default account still exposes the shared archive-vs-delete rule, now
        // wrapped in AccountRetireOffer.Retire (see AccountRetireOfferTest).
        assertEquals(AccountRetireOffer.Retire(RetireAction.ARCHIVE), moved.retireOffer)
        assertEquals(AccountRetireOffer.Retire(RetireAction.DELETE), moved.copy(hasMovement = false).retireOffer)
    }
}
