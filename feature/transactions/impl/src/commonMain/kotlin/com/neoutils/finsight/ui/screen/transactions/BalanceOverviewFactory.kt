package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.ui.screen.transactions.TransactionsUiState.BalanceOverview
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth

/**
 * The summary of one perimeter, read from the ledger — never summed over the list the
 * screen happens to have loaded. The three scopes share one grammar: the opening and
 * closing figures are the accumulated balance of the perimeter's nature, and the flow
 * lines partition the perimeter's entries in the month. A movement whose legs all fall
 * inside the perimeter contributes zero by `Σ = 0`, so it needs no exception of its own.
 */
internal suspend fun IEntryRepository.balanceOverview(
    scope: TransactionScope,
    month: YearMonth,
): BalanceOverview {
    val previous = month.minusMonth()

    return when (scope) {
        TransactionScope.ACCOUNTS -> {
            val flows = assetMonthFlows(month)
            BalanceOverview.Accounts(
                openingBalance = naturalBalanceUpTo(previous, AccountType.ASSET),
                income = flows.income,
                expense = flows.expense,
                // An invoice payment has a leg outside this perimeter, so it *is* a
                // flow here — unlike a transfer, whose two legs are both inside.
                invoicePayment = liabilityMonthFlows(month).payment.orNullIfZero(),
                adjustment = flows.adjustment.orNullIfZero(),
                finalBalance = naturalBalanceUpTo(month, AccountType.ASSET),
            )
        }

        TransactionScope.CARDS -> {
            val flows = liabilityMonthFlows(month)
            // The ledger's own sign, so the column reads like a statement and still
            // closes: opening − spending + payments + adjustment.
            BalanceOverview.Cards(
                openingBalance = naturalBalanceUpTo(previous, AccountType.LIABILITY),
                expense = flows.expense,
                payment = flows.payment.orNullIfZero(),
                adjustment = flows.adjustment.orNullIfZero(),
                finalBalance = naturalBalanceUpTo(month, AccountType.LIABILITY),
            )
        }

        TransactionScope.ALL -> {
            val asset = assetMonthFlows(month)
            val liability = liabilityMonthFlows(month)
            BalanceOverview.Overall(
                openingNet = netBalanceUpTo(previous),
                income = asset.income,
                // Disjoint sets — a card purchase has no ASSET leg — so aggregating
                // them cannot double-count. Which book the money left is the scope's
                // question, not the summary's.
                expense = asset.expense + liability.expense,
                // Both legs are inside this perimeter: shown because the user asks for
                // it, outside the sum because it moves nothing.
                invoicePayment = liability.payment.orNullIfZero(),
                adjustment = (asset.adjustment + liability.adjustment).orNullIfZero(),
                finalNet = netBalanceUpTo(month),
            )
        }
    }
}

/**
 * The consolidated figure is the **sum** of the two natures, not an aggregate of its
 * own: liabilities are stored in credit, so no sign rule is needed here either.
 */
private suspend fun IEntryRepository.netBalanceUpTo(month: YearMonth): Double =
    naturalBalanceUpTo(month, AccountType.ASSET) + naturalBalanceUpTo(month, AccountType.LIABILITY)

/** A flow the month does not have is an absent line, not a zero the card must hide. */
private fun Double.orNullIfZero(): Double? = takeIf { it != 0.0 }
