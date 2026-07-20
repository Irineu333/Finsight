package com.neoutils.finsight.extension

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionType

/**
 * The natural (debit-positive) balance of an account: the sum of the signed
 * amounts of every entry that references it. This is the single mechanism from
 * which account balance, category spending and net worth are all derived.
 */
fun List<Entry>.naturalBalanceOf(accountId: Long): Long =
    filter { it.account.id == accountId }.sumOf { it.amount }

/**
 * The sign applied when presenting a natural balance to the user, so that
 * credit-natured accounts (`LIABILITY`/`INCOME`/`EQUITY`) read positive.
 */
val AccountType.displaySign: Int get() = if (isDebitNatured) 1 else -1

/**
 * A natural balance converted to the sign the user expects to see for [type].
 */
fun AccountType.displayBalance(naturalBalance: Long): Long = naturalBalance * displaySign

/**
 * The closed *permanent* leg that makes this transaction immutable, or `null` when
 * it can still be changed.
 *
 * Archiving an ASSET/LIABILITY requires a zero balance, so any change that moves
 * movement off it — deleting the transaction, or editing it to point somewhere
 * else — reopens a balance on an account that accepts no new entries and appears
 * in no selector, with no screen able to zero it again.
 *
 * **Editing counts.** A rewrite deletes the old legs and writes new ones, so the
 * write boundary sees only open accounts on the way in and waves it through: the
 * damage is entirely on the side that was *removed*. Checking the new legs is
 * not enough, and never was.
 *
 * A category is the exception, for the same reason it archives at any balance:
 * it is not monetary, its balance is a period total, not money sitting anywhere.
 *
 * Derived, never persisted, and owned here so the write boundary that refuses the
 * change and the screens that decline to offer it cannot disagree.
 */
fun List<Entry>.closedLegBlockingChange(): Entry? =
    firstOrNull { it.account.isArchived && it.account.type.isPermanent }

/**
 * Derives the transaction label from the account types of its entries, in a single
 * place, without consulting any persisted kind. `EQUITY` is tested *before any
 * other case*: a reconciliation counter-leg makes the transaction an adjustment
 * regardless of where the money sits — otherwise `{ASSET, EQUITY}` would fall
 * through to `TRANSFER` and `{LIABILITY, EQUITY}` would be caught by `LIABILITY`
 * as `PAYMENT`. After it, an `EXPENSE` account makes it an expense; an `INCOME`
 * account an income; a `LIABILITY` account a payment; otherwise (two `ASSET`
 * accounts) a transfer. Total over the seven ledger forms.
 */
fun List<Entry>.deriveTransactionLabel(): TransactionLabel {
    val types = mapTo(mutableSetOf()) { it.account.type }
    return when {
        AccountType.EQUITY in types -> TransactionLabel.ADJUSTMENT
        AccountType.EXPENSE in types -> TransactionLabel.EXPENSE
        AccountType.INCOME in types -> TransactionLabel.INCOME
        AccountType.LIABILITY in types -> TransactionLabel.PAYMENT
        else -> TransactionLabel.TRANSFER
    }
}

/** True when the entries balance to zero for every currency present. */
fun List<Entry>.isBalanced(): Boolean =
    groupBy { it.currency }.all { (_, entries) -> entries.sumOf { it.amount } == 0L }

/**
 * Derives a money leg's [TransactionType] from the ledger, so it need not be
 * persisted. The user's intent is recoverable from the transaction's contra leg:
 * an `EQUITY` counter-leg means a balance adjustment; otherwise the sign of the
 * leg's own entry gives the direction (money out = expense, money in = income).
 * Holds for both the `ASSET` account leg and the `LIABILITY` card leg.
 */
fun deriveTransactionType(legAmountCents: Long, transactionEntries: List<Entry>): TransactionType = when {
    transactionEntries.any { it.account.type == AccountType.EQUITY } -> TransactionType.ADJUSTMENT
    legAmountCents < 0 -> TransactionType.EXPENSE
    else -> TransactionType.INCOME
}
