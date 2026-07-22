package com.neoutils.finsight.extension

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LedgerTest {

    private fun account(id: Long, type: AccountType) = Account(id = id, name = "acc$id", type = type)

    private fun entry(type: AccountType, amount: Long, accountId: Long = type.ordinal.toLong()) =
        Entry(account = account(accountId, type), amount = amount)

    // --- deriveTransactionLabel (task 1.5: label from account types) ---

    @Test
    fun `asset and expense legs derive an expense`() {
        val entries = listOf(entry(AccountType.ASSET, -5000), entry(AccountType.EXPENSE, 5000))
        assertEquals(TransactionLabel.EXPENSE, entries.deriveTransactionLabel())
    }

    @Test
    fun `income and asset legs derive an income`() {
        val entries = listOf(entry(AccountType.ASSET, 5000), entry(AccountType.INCOME, -5000))
        assertEquals(TransactionLabel.INCOME, entries.deriveTransactionLabel())
    }

    @Test
    fun `two asset legs derive a transfer`() {
        val entries = listOf(entry(AccountType.ASSET, -10000, 1), entry(AccountType.ASSET, 10000, 2))
        assertEquals(TransactionLabel.TRANSFER, entries.deriveTransactionLabel())
    }

    @Test
    fun `asset and liability legs derive a payment`() {
        val entries = listOf(entry(AccountType.ASSET, -5000), entry(AccountType.LIABILITY, 5000))
        assertEquals(TransactionLabel.PAYMENT, entries.deriveTransactionLabel())
    }

    // --- EQUITY is evaluated first: both adjustment forms label ADJUSTMENT (task 1.4) ---

    @Test
    fun `balance adjustment of an account derives an adjustment rather than a transfer`() {
        // {ASSET, EQUITY}: no EXPENSE/INCOME/LIABILITY case matches, so without the
        // EQUITY-first branch it would fall through to TRANSFER.
        val entries = listOf(entry(AccountType.ASSET, 3000), entry(AccountType.EQUITY, -3000))
        assertEquals(TransactionLabel.ADJUSTMENT, entries.deriveTransactionLabel())
    }

    @Test
    fun `balance adjustment of an invoice derives an adjustment rather than a payment`() {
        // {LIABILITY, EQUITY}: LIABILITY would be caught first as PAYMENT unless
        // EQUITY is tested before every other case.
        val entries = listOf(entry(AccountType.LIABILITY, -3000), entry(AccountType.EQUITY, 3000))
        assertEquals(TransactionLabel.ADJUSTMENT, entries.deriveTransactionLabel())
    }

    // --- isBalanced (Σ = 0 per currency) ---

    @Test
    fun `entries summing to zero are balanced`() {
        assertTrue(listOf(entry(AccountType.ASSET, -5000), entry(AccountType.EXPENSE, 5000)).isBalanced())
    }

    @Test
    fun `entries not summing to zero are unbalanced`() {
        assertFalse(listOf(entry(AccountType.ASSET, -5000), entry(AccountType.EXPENSE, 4000)).isBalanced())
    }

    @Test
    fun `balance is checked per currency`() {
        val mixed = listOf(
            Entry(account = account(1, AccountType.ASSET), amount = -5000, currency = "BRL"),
            Entry(account = account(2, AccountType.EXPENSE), amount = 5000, currency = "BRL"),
            Entry(account = account(3, AccountType.ASSET), amount = -100, currency = "USD"),
        )
        assertFalse(mixed.isBalanced()) // USD does not sum to zero
    }

    // --- naturalBalanceOf ---

    @Test
    fun `natural balance sums the entries of one account`() {
        val entries = listOf(
            Entry(account = account(1, AccountType.ASSET), amount = 5000),
            Entry(account = account(1, AccountType.ASSET), amount = -2000),
            Entry(account = account(2, AccountType.ASSET), amount = 9999),
        )
        assertEquals(3000L, entries.naturalBalanceOf(1))
    }

    // --- display sign inversion by AccountType ---

    @Test
    fun `debit-natured accounts read their natural balance`() {
        assertEquals(5000L, AccountType.ASSET.displayBalance(5000))
        assertEquals(5000L, AccountType.EXPENSE.displayBalance(5000))
    }

    @Test
    fun `credit-natured accounts invert for display`() {
        assertEquals(3000L, AccountType.LIABILITY.displayBalance(-3000))
        assertEquals(3000L, AccountType.INCOME.displayBalance(-3000))
        assertEquals(3000L, AccountType.EQUITY.displayBalance(-3000))
    }

    // --- deriveTransactionType (recovers the leg's type from the ledger) ---

    @Test
    fun `expense leg derives from a negative amount`() {
        val op = listOf(entry(AccountType.ASSET, -5000), entry(AccountType.EXPENSE, 5000))
        assertEquals(TransactionType.EXPENSE, deriveTransactionType(-5000, op))
    }

    @Test
    fun `income leg derives from a positive amount`() {
        val op = listOf(entry(AccountType.ASSET, 5000), entry(AccountType.INCOME, -5000))
        assertEquals(TransactionType.INCOME, deriveTransactionType(5000, op))
    }

    @Test
    fun `positive adjustment is distinguished from income by its EQUITY counter-leg`() {
        val op = listOf(entry(AccountType.ASSET, 3000), entry(AccountType.EQUITY, -3000))
        // Same positive sign as income, but the EQUITY leg makes it an adjustment.
        assertEquals(TransactionType.ADJUSTMENT, deriveTransactionType(3000, op))
    }

    @Test
    fun `negative adjustment is still an adjustment`() {
        val op = listOf(entry(AccountType.ASSET, -3000), entry(AccountType.EQUITY, 3000))
        assertEquals(TransactionType.ADJUSTMENT, deriveTransactionType(-3000, op))
    }

    @Test
    fun `card purchase liability leg derives as expense`() {
        val op = listOf(entry(AccountType.LIABILITY, -5000), entry(AccountType.EXPENSE, 5000))
        assertEquals(TransactionType.EXPENSE, deriveTransactionType(-5000, op))
    }

    @Test
    fun `invoice payment card leg derives as income`() {
        val op = listOf(entry(AccountType.ASSET, -5000), entry(AccountType.LIABILITY, 5000))
        assertEquals(TransactionType.INCOME, deriveTransactionType(5000, op))
    }

    @Test
    fun `account nature partitions debit and credit`() {
        assertTrue(AccountType.ASSET.isDebitNatured && AccountType.EXPENSE.isDebitNatured)
        assertTrue(AccountType.LIABILITY.isCreditNatured && AccountType.INCOME.isCreditNatured && AccountType.EQUITY.isCreditNatured)
    }

    // --- isMonetary: the money-bearing types vs the synthesized counter-legs (task 1.2) ---

    @Test
    fun `monetary types are the ones that hold money`() {
        assertTrue(AccountType.ASSET.isMonetary)
        assertTrue(AccountType.LIABILITY.isMonetary)
        assertFalse(AccountType.INCOME.isMonetary)
        assertFalse(AccountType.EXPENSE.isMonetary)
        assertFalse(AccountType.EQUITY.isMonetary)
    }

    // --- isPermanent: real vs nominal accounts, which is what decides whether a
    // --- balance can be stranded by archiving.
    @Test
    fun `permanent accounts are the ones whose balance carries across periods`() {
        assertTrue(AccountType.ASSET.isPermanent)
        assertTrue(AccountType.LIABILITY.isPermanent)
        assertTrue(AccountType.EQUITY.isPermanent)
        // Temporary: their balance is a period total, zeroed only by a closing entry.
        assertFalse(AccountType.INCOME.isPermanent)
        assertFalse(AccountType.EXPENSE.isPermanent)
    }

    // --- closedLegBlockingChange: the delete gate, shared by the write boundary
    // and by the screens that decide whether to offer deleting.

    private fun archived(type: AccountType, id: Long) = Entry(
        account = Account(id = id, name = "acc$id", type = type, isArchived = true),
        amount = 0,
    )

    @Test
    fun `entries on open accounts block no removal`() {
        val entries = listOf(entry(AccountType.ASSET, -5000), entry(AccountType.EXPENSE, 5000))
        assertEquals(null, entries.closedLegBlockingChange())
    }

    @Test
    fun `an archived asset leg blocks the removal`() {
        // Archiving it required a zero balance; taking the movement away reopens one.
        val entries = listOf(archived(AccountType.ASSET, 1), entry(AccountType.EXPENSE, 5000))
        assertEquals(1L, entries.closedLegBlockingChange()?.account?.id)
    }

    @Test
    fun `an archived liability leg blocks the removal`() {
        val entries = listOf(archived(AccountType.LIABILITY, 2), entry(AccountType.ASSET, 5000))
        assertEquals(2L, entries.closedLegBlockingChange()?.account?.id)
    }

    @Test
    fun `an archived category leg blocks nothing`() {
        // A category archives at any balance, so its movement strands nothing. Without
        // this the gate would be a blanket "archived", not the mirror of the
        // precondition that let the account close.
        val entries = listOf(entry(AccountType.ASSET, -5000), archived(AccountType.EXPENSE, 3))
        assertEquals(null, entries.closedLegBlockingChange())
    }
}
