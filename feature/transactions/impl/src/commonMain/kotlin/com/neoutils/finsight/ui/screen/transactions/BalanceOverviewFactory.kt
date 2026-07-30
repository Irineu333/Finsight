package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.ASSUMED_SINGLE_CURRENCY
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.extension.Denomination
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.ui.screen.transactions.TransactionsUiState.BalanceOverview
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth

/**
 * The summary of one perimeter, read from the ledger — never summed over the list the
 * screen happens to have loaded. The three scopes share one grammar: the opening and
 * closing figures are the accumulated balance of the perimeter's nature, and the flow
 * lines partition the perimeter's entries in the month. A movement whose legs all fall
 * inside the perimeter contributes zero by `Σ = 0`, so it needs no exception of its own.
 *
 * Each figure leaves here with its sign policy already attached, so the card only
 * renders it. The policies *are* the rule of the summary surface — the sign is the effect
 * of that figure on **this** perimeter's net worth:
 *
 * - spending always subtracts and income always adds, in whichever perimeter is asked;
 * - an invoice payment leaves the `ASSET` perimeter and lands in the `LIABILITY` one, so
 *   it is negative in the first and positive in the second — and *neutral* overall, where
 *   both of its legs are inside and it moves nothing. It is shown there anyway, because
 *   the user asks for it, and signless precisely so the column above still adds up;
 * - an adjustment is the only line whose direction the label withholds, so it is spelled
 *   out in both directions, exactly as it reads as an item;
 * - a balance shows only its negative, while a *debt* line answers "how much is owed" and
 *   carries no sign at all. Reading the card's book the other way round — debt positive —
 *   would make spending read `+90`, which is not how anyone reads a statement.
 */
internal suspend fun IEntryRepository.balanceOverview(
    scope: TransactionScope,
    month: YearMonth,
): BalanceOverview {
    val previous = month.minusMonth()

    // Every figure here aggregates across accounts, so its currency is not the currency
    // of any one of them.
    val denomination = Denomination.exact(ASSUMED_SINGLE_CURRENCY)

    return when (scope) {
        TransactionScope.ACCOUNTS -> {
            val flows = assetMonthFlows(month)
            BalanceOverview.Accounts(
                openingBalance = DisplayAmount.natural(naturalBalanceUpTo(previous, AccountType.ASSET), denomination),
                income = DisplayAmount.forcedPositive(flows.income, denomination),
                expense = DisplayAmount.forcedNegative(flows.expense, denomination),
                // An invoice payment has a leg outside this perimeter, so it *is* a
                // flow here — unlike a transfer, whose two legs are both inside.
                invoicePayment = DisplayAmount.forcedNegative(liabilityMonthFlows(month).payment, denomination)
                    .orNullIfZero(),
                adjustment = DisplayAmount.explicitSign(flows.adjustment, denomination).orNullIfZero(),
                finalBalance = DisplayAmount.natural(naturalBalanceUpTo(month, AccountType.ASSET), denomination),
            )
        }

        TransactionScope.CARDS -> {
            val flows = liabilityMonthFlows(month)
            // The ledger's own sign, so the column reads like a statement and still
            // closes: opening − spending + payments + adjustment.
            BalanceOverview.Cards(
                openingBalance = DisplayAmount.owed(naturalBalanceUpTo(previous, AccountType.LIABILITY), denomination),
                expense = DisplayAmount.forcedNegative(flows.expense, denomination),
                payment = DisplayAmount.forcedPositive(flows.payment, denomination).orNullIfZero(),
                adjustment = DisplayAmount.explicitSign(flows.adjustment, denomination).orNullIfZero(),
                finalBalance = DisplayAmount.owed(naturalBalanceUpTo(month, AccountType.LIABILITY), denomination),
            )
        }

        TransactionScope.ALL -> {
            val asset = assetMonthFlows(month)
            val liability = liabilityMonthFlows(month)
            BalanceOverview.Overall(
                openingNet = DisplayAmount.natural(netBalanceUpTo(previous), denomination),
                income = DisplayAmount.forcedPositive(asset.income, denomination),
                // Disjoint sets — a card purchase has no ASSET leg — so aggregating
                // them cannot double-count. Which book the money left is the scope's
                // question, not the summary's.
                expense = DisplayAmount.forcedNegative(asset.expense + liability.expense, denomination),
                // Both legs are inside this perimeter: shown because the user asks for
                // it, outside the sum because it moves nothing.
                invoicePayment = DisplayAmount.neutral(liability.payment, denomination).orNullIfZero(),
                adjustment = DisplayAmount.explicitSign(asset.adjustment + liability.adjustment, denomination)
                    .orNullIfZero(),
                finalNet = DisplayAmount.natural(netBalanceUpTo(month), denomination),
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
private fun DisplayAmount.orNullIfZero(): DisplayAmount? = takeIf { it.value != 0.0 }
