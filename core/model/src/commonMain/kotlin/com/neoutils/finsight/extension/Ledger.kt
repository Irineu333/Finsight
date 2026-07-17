package com.neoutils.finsight.extension

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.OperationLabel
import com.neoutils.finsight.domain.model.Transaction
import kotlin.math.roundToLong

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
 * Derives the operation label from the account types of its entries, in a single
 * place, without consulting any persisted kind. `EQUITY` is tested *before any
 * other case*: a reconciliation counter-leg makes the operation an adjustment
 * regardless of where the money sits — otherwise `{ASSET, EQUITY}` would fall
 * through to `TRANSFER` and `{LIABILITY, EQUITY}` would be caught by `LIABILITY`
 * as `PAYMENT`. After it, an `EXPENSE` account makes it an expense; an `INCOME`
 * account an income; a `LIABILITY` account a payment; otherwise (two `ASSET`
 * accounts) a transfer. Total over the seven ledger forms.
 */
fun List<Entry>.deriveOperationLabel(): OperationLabel {
    val types = mapTo(mutableSetOf()) { it.account.type }
    return when {
        AccountType.EQUITY in types -> OperationLabel.ADJUSTMENT
        AccountType.EXPENSE in types -> OperationLabel.EXPENSE
        AccountType.INCOME in types -> OperationLabel.INCOME
        AccountType.LIABILITY in types -> OperationLabel.PAYMENT
        else -> OperationLabel.TRANSFER
    }
}

/** True when the entries balance to zero for every currency present. */
fun List<Entry>.isBalanced(): Boolean =
    groupBy { it.currency }.all { (_, entries) -> entries.sumOf { it.amount } == 0L }

/**
 * The signed amount, in cents, that a legacy [Transaction] contributes to the
 * natural (debit-positive) balance of its own account. This is the bridge from
 * the legacy `Double`/`Type` model to the ledger: it equals `signedImpact * 100`
 * and holds for both `ASSET` and the `LIABILITY` card leg. Removed with the
 * legacy model in the final cleanup.
 */
fun Transaction.signedCents(): Long {
    val cents = (amount * 100).roundToLong()
    return when (type) {
        Transaction.Type.EXPENSE -> -cents
        Transaction.Type.INCOME -> cents
        Transaction.Type.ADJUSTMENT -> cents
    }
}

/**
 * Derives a money leg's [Transaction.Type] from the ledger, so it need not be
 * persisted. The user's intent is recoverable from the operation's contra leg:
 * an `EQUITY` counter-leg means a balance adjustment; otherwise the sign of the
 * leg's own entry gives the direction (money out = expense, money in = income).
 * Holds for both the `ASSET` account leg and the `LIABILITY` card leg.
 */
fun deriveTransactionType(legAmountCents: Long, operationEntries: List<Entry>): Transaction.Type = when {
    operationEntries.any { it.account.type == AccountType.EQUITY } -> Transaction.Type.ADJUSTMENT
    legAmountCents < 0 -> Transaction.Type.EXPENSE
    else -> Transaction.Type.INCOME
}
