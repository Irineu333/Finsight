package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies task 2.7: the [OperationMapper] carries the operation's hydrated ledger
 * entries onto the domain [com.neoutils.finsight.domain.model.Operation], making them
 * available to every consumer.
 */
class OperationMapperEntriesTest {

    @Test
    fun `mapper carries the hydrated entries onto the operation`() {
        val account = Account(id = 1, name = "A", type = AccountType.ASSET)
        val leg = Transaction(
            type = TransactionType.EXPENSE, amount = 50.0, title = null,
            date = LocalDate(2026, 3, 10), account = account,
        )
        val entries = listOf(
            Entry(id = 1, transactionId = 7, account = account, amount = -5000),
            Entry(id = 2, transactionId = 7, account = Account(id = 10, name = "Food", type = AccountType.EXPENSE), amount = 5000),
        )

        val operation = OperationMapper().toDomain(
            entity = TransactionEntity(id = 7, title = null, date = LocalDate(2026, 3, 10), categoryId = null),
            transactions = listOf(leg),
            categories = emptyMap(),
            creditCards = emptyMap(),
            invoices = emptyMap(),
            installments = emptyMap(),
            accounts = mapOf(1L to account),
            recurring = emptyMap(),
            entries = entries,
        )

        assertEquals(entries, operation?.entries)
    }
}
