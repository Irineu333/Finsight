package com.neoutils.finsight.ui.model

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

    @Test
    fun `the ui models expose the same rule so the two screens cannot drift`() {
        val moved = AccountUi(
            id = 1, openingBalance = 0.0, balance = 0.0, income = 0.0,
            expense = 0.0, adjustment = 0.0, settlement = 0.0, hasMovement = true,
        )
        assertEquals(RetireAction.ARCHIVE, moved.retireAction)
        assertEquals(RetireAction.DELETE, moved.copy(hasMovement = false).retireAction)
    }
}
