package com.neoutils.finsight.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `hasNominalLeg` is what separates "has an analytic axis" from "has none". The three
 * negative cases are the reason it exists: their `nominalDimensionId` is null too, and
 * reading that alone would call them unclassified.
 */
class TransactionNominalLegTest {

    private fun account(id: Long, type: AccountType) =
        Account(id = id, name = "acc$id", type = type, currency = "BRL")

    private fun transaction(vararg entries: Entry) = Transaction(
        title = null,
        date = LocalDate(2026, 1, 15),
        entries = entries.toList(),
    )

    private fun entry(type: AccountType, amount: Long, accountId: Long = type.ordinal.toLong()) =
        Entry(account = account(accountId, type), amount = amount)

    @Test
    fun `an expense has a nominal leg`() {
        val expense = transaction(
            entry(AccountType.ASSET, -5_000),
            entry(AccountType.EXPENSE, 5_000),
        )

        assertTrue(expense.hasNominalLeg)
    }

    @Test
    fun `an income has a nominal leg`() {
        val income = transaction(
            entry(AccountType.ASSET, 5_000),
            entry(AccountType.INCOME, -5_000),
        )

        assertTrue(income.hasNominalLeg)
    }

    @Test
    fun `a transfer between accounts has none`() {
        val transfer = transaction(
            entry(AccountType.ASSET, -10_000, accountId = 1),
            entry(AccountType.ASSET, 10_000, accountId = 2),
        )

        assertFalse(transfer.hasNominalLeg)
        // The trap this guards: the two facts agree on null, and only one of them means
        // "unclassified".
        assertTrue(transfer.nominalDimensionId == null)
    }

    @Test
    fun `an invoice payment has none`() {
        val payment = transaction(
            entry(AccountType.ASSET, -5_000),
            entry(AccountType.LIABILITY, 5_000),
        )

        assertFalse(payment.hasNominalLeg)
        assertTrue(payment.nominalDimensionId == null)
    }

    @Test
    fun `a balance adjustment has none`() {
        val adjustment = transaction(
            entry(AccountType.ASSET, 3_000),
            entry(AccountType.EQUITY, -3_000),
        )

        assertFalse(adjustment.hasNominalLeg)
        assertTrue(adjustment.nominalDimensionId == null)
    }
}
