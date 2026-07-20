package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Verifies task 2.7: the [TransactionMapper] carries the transaction's hydrated ledger
 * entries onto the domain [com.neoutils.finsight.domain.model.Transaction], making them
 * available to every consumer.
 */
class TransactionMapperEntriesTest {

    private val entity = TransactionEntity(id = 7, title = null, date = LocalDate(2026, 3, 10), categoryId = null)

    private fun toDomain(entries: List<Entry>) = TransactionMapper().toDomain(
        entity = entity,
        categories = emptyMap(),
        creditCards = emptyMap(),
        invoices = emptyMap(),
        installments = emptyMap(),
        recurring = emptyMap(),
        entries = entries,
    )

    @Test
    fun `mapper carries the hydrated entries onto the transaction`() {
        val account = Account(id = 1, name = "A", type = AccountType.ASSET)
        val entries = listOf(
            Entry(id = 1, transactionId = 7, account = account, amount = -5000),
            Entry(id = 2, transactionId = 7, account = Account(id = 10, name = "Food", type = AccountType.EXPENSE), amount = 5000),
        )

        assertEquals(entries, toDomain(entries)?.entries)
    }

    @Test
    fun `mapper returns null when the transaction has no entries`() {
        assertNull(toDomain(emptyList()))
    }
}
