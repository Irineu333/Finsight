package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Fixes the invariant left after the D6 fallback dissolved (D21): a
 * [TransactionPerspective] is a plain data class always constructible from an
 * account id — there is no nullable fallback — and it resolves the leg of that
 * account. A card enters the same way, through its `accountId`.
 */
class TransactionPerspectiveTest {

    private val date = LocalDate(2026, 1, 1)

    private fun leg(account: Account) = Transaction(
        type = TransactionType.EXPENSE,
        amount = 100.0,
        title = null,
        date = date,
        account = account,
    )

    private fun operation(vararg legs: Transaction) = Operation(
        id = 1L,
        title = "Op",
        date = date,
        transactions = legs.toList(),
    )

    @Test
    fun resolvesLegOfItsAccount() {
        val source = Account(id = 1L, name = "Source", type = AccountType.ASSET)
        val destination = Account(id = 2L, name = "Destination", type = AccountType.ASSET)
        val operation = operation(leg(source), leg(destination))

        assertEquals(source.id, TransactionPerspective(accountId = 1L).resolve(operation)?.account?.id)
        assertEquals(destination.id, TransactionPerspective(accountId = 2L).resolve(operation)?.account?.id)
    }

    @Test
    fun resolvesNullWhenNoLegMatches() {
        val operation = operation(leg(Account(id = 1L, name = "Source", type = AccountType.ASSET)))

        assertNull(TransactionPerspective(accountId = 99L).resolve(operation))
    }
}
