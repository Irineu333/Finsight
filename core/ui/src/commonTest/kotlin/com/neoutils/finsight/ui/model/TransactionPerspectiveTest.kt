package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Fixes the invariant left after the D6 fallback dissolved (D21): a
 * [TransactionPerspective] is an account id — a card enters the same way, through
 * its `accountId` — and it decides which leg of a transaction the screen reads.
 * The same transfer read from its two accounts yields opposite directions and opposite
 * signs; an account with no leg yields no item at all.
 */
class TransactionPerspectiveTest {

    private val source = Account(id = 1L, name = "Source", type = AccountType.ASSET)
    private val destination = Account(id = 2L, name = "Destination", type = AccountType.ASSET)

    private val transfer = Transaction(
        id = 1L,
        title = "Op",
        date = LocalDate(2026, 1, 1),
        entries = listOf(
            Entry(account = source, amount = -10_000),
            Entry(account = destination, amount = 10_000),
        ),
    )

    private fun uiFrom(accountId: Long) = transfer.toTransactionUi(TransactionPerspective(accountId).accountId)

    @Test
    fun perspectiveSelectsTheLegOfItsAccount() {
        val outgoing = uiFrom(source.id)
        val incoming = uiFrom(destination.id)

        assertEquals(TransactionType.EXPENSE, outgoing?.direction)
        assertEquals(TransactionType.INCOME, incoming?.direction)
        // Under a perspective a transfer is signed at both ends — the two legs share
        // label, icon and color, so the sign is all that tells them apart.
        assertEquals(DisplayAmount.explicitSign(-100.0), outgoing?.amount)
        assertEquals(DisplayAmount.explicitSign(100.0), incoming?.amount)
        // The transaction's nature does not depend on who is looking.
        assertEquals(TransactionLabel.TRANSFER, outgoing?.label)
        assertEquals(TransactionLabel.TRANSFER, incoming?.label)
    }

    @Test
    fun perspectiveWithoutALegYieldsNoItem() {
        assertNull(uiFrom(accountId = 99L))
    }
}
