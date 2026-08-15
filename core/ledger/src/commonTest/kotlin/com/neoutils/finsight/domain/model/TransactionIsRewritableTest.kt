package com.neoutils.finsight.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `isRewritable` is the precondition of `ITransactionRepository.updateTransaction`, and it has
 * one owner because it has two consumers: the edit screen decides whether to offer the button,
 * and the MCP surface decides whether to accept the call. Before it existed here, each spelled
 * the rule out for itself — and the second one had already lost a gate.
 *
 * The four gates are tested one at a time, each with everything else valid, so a gate that
 * stops holding fails alone.
 */
class TransactionIsRewritableTest {

    private fun account(
        id: Long,
        type: AccountType,
        isArchived: Boolean = false,
    ) = Account(id = id, name = "acc$id", type = type, currency = "BRL", isArchived = isArchived)

    private fun entry(account: Account, amount: Long) = Entry(account = account, amount = amount)

    private val asset = account(1, AccountType.ASSET)
    private val expense = account(2, AccountType.EXPENSE)

    private fun transaction(
        entries: List<Entry>,
        installmentId: Long? = null,
    ) = Transaction(
        title = null,
        date = LocalDate(2026, 1, 15),
        installmentId = installmentId,
        entries = entries,
    )

    @Test
    fun `an ordinary expense can be rewritten`() {
        val expenseTransaction = transaction(
            listOf(entry(asset, -5_000), entry(expense, 5_000)),
        )

        assertTrue(expenseTransaction.isRewritable)
    }

    @Test
    fun `an adjustment cannot`() {
        // It states a balance rather than a movement; rewriting it would restate the balance
        // as if it were a movement.
        val adjustment = transaction(
            listOf(entry(asset, 5_000), entry(account(3, AccountType.EQUITY), -5_000)),
        )

        assertFalse(adjustment.isRewritable)
    }

    @Test
    fun `two monetary legs cannot — the rewrite states one`() {
        // A transfer. The rewrite deletes every entry and rebuilds from the single leg the
        // caller describes, so the second one would disappear without a word.
        val transfer = transaction(
            listOf(entry(asset, -5_000), entry(account(4, AccountType.ASSET), 5_000)),
        )

        assertFalse(transfer.isRewritable)
    }

    @Test
    fun `one payment of an installment plan cannot`() {
        val installment = transaction(
            entries = listOf(entry(asset, -5_000), entry(expense, 5_000)),
            installmentId = 7,
        )

        assertFalse(installment.isRewritable)
    }

    @Test
    fun `a leg on a closed account cannot`() {
        // The account accepts no further entries, and a rewrite writes entries again. This is
        // the gate the MCP tool was missing while the screen had it.
        val frozen = transaction(
            listOf(entry(account(5, AccountType.ASSET, isArchived = true), -5_000), entry(expense, 5_000)),
        )

        assertFalse(frozen.isRewritable)
    }
}
