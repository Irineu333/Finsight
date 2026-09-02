package com.neoutils.finsight

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The read a list makes when it already holds the identities.**
 *
 * The two reads beside it are both wrong for that: one row at a time is the N+1 a list
 * pays on every emission, and the whole ledger is a walk over everything to keep a
 * handful. What this one owes is that the batch comes back **hydrated** — a transaction
 * without its legs has no figure at all — and that an id with no row is an absence and
 * not a failure.
 *
 * Over a real database, because what is being asserted is what the query returns: a fake
 * would answer whatever the test put in it, including for the id that is not there.
 */
class TransactionsByIdsTest {

    private val date = LocalDate(2026, 8, 5)

    @Test
    fun `it returns the transactions asked for, hydrated with their legs`() = runApp("BRL") {
        val wallet = account("Wallet", "BRL")
        val groceries = category("Mercado")

        val first = expense(wallet, 86.5, date, groceries)
        val second = expense(wallet, 12.0, date, groceries)
        // A third one exists and is not asked for: the read is by ids, not "everything".
        expense(wallet, 999.0, date, groceries)

        val read = transactions.getTransactionsByIds(listOf(first.id, second.id))

        assertEquals(setOf(first.id, second.id), read.map { it.id }.toSet())
        assertTrue(read.all { it.entries.isNotEmpty() })
        assertEquals(
            setOf(86.5, 12.0),
            read.map { it.amount }.toSet(),
        )
    }

    @Test
    fun `an id with no row is an absence, not an error`() = runApp("BRL") {
        val wallet = account("Wallet", "BRL")
        val only = expense(wallet, 40.0, date)

        val read = transactions.getTransactionsByIds(listOf(only.id, only.id + 999))

        assertEquals(listOf(only.id), read.map { it.id })
    }

    @Test
    fun `asking for nothing reads nothing`() = runApp("BRL") {
        val wallet = account("Wallet", "BRL")
        expense(wallet, 40.0, date)

        assertEquals(emptyList(), transactions.getTransactionsByIds(emptyList()))
    }
}
