package com.neoutils.finsight.extension

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.OperationLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LedgerTest {

    private fun account(id: Long, type: AccountType) = Account(id = id, name = "acc$id", type = type)

    private fun entry(type: AccountType, amount: Long, accountId: Long = type.ordinal.toLong()) =
        Entry(account = account(accountId, type), amount = amount)

    // --- deriveOperationLabel (task 1.5: label from account types) ---

    @Test
    fun `asset and expense legs derive an expense`() {
        val entries = listOf(entry(AccountType.ASSET, -5000), entry(AccountType.EXPENSE, 5000))
        assertEquals(OperationLabel.EXPENSE, entries.deriveOperationLabel())
    }

    @Test
    fun `income and asset legs derive an income`() {
        val entries = listOf(entry(AccountType.ASSET, 5000), entry(AccountType.INCOME, -5000))
        assertEquals(OperationLabel.INCOME, entries.deriveOperationLabel())
    }

    @Test
    fun `two asset legs derive a transfer`() {
        val entries = listOf(entry(AccountType.ASSET, -10000, 1), entry(AccountType.ASSET, 10000, 2))
        assertEquals(OperationLabel.TRANSFER, entries.deriveOperationLabel())
    }

    @Test
    fun `asset and liability legs derive a payment`() {
        val entries = listOf(entry(AccountType.ASSET, -5000), entry(AccountType.LIABILITY, 5000))
        assertEquals(OperationLabel.PAYMENT, entries.deriveOperationLabel())
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

    @Test
    fun `account nature partitions debit and credit`() {
        assertTrue(AccountType.ASSET.isDebitNatured && AccountType.EXPENSE.isDebitNatured)
        assertTrue(AccountType.LIABILITY.isCreditNatured && AccountType.INCOME.isCreditNatured && AccountType.EQUITY.isCreditNatured)
    }
}
