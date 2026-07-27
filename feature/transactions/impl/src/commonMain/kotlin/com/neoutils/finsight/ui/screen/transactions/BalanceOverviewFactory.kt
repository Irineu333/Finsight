package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.repository.IEntryRepository
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
 *
 * [yieldDimensionId] is what separates yield from ordinary income. The line appears
 * when the period actually holds one, and not on any account's declaration: the summary
 * has nothing to launch from, so a line at zero here would say nothing — while a yield
 * already recorded must stay visible however the account is configured now, since
 * `income` no longer contains it.
 */
internal suspend fun IEntryRepository.balanceOverview(
    scope: TransactionScope,
    month: YearMonth,
    yieldDimensionId: Long? = null,
): BalanceOverview {
    val previous = month.minusMonth()

    return when (scope) {
        TransactionScope.ACCOUNTS -> {
            val flows = assetMonthFlows(month, yieldDimensionId)
            BalanceOverview.Accounts(
                openingBalance = DisplayAmount.natural(naturalBalanceUpTo(previous, AccountType.ASSET)),
                income = DisplayAmount.forcedPositive(flows.income),
                yield = DisplayAmount.forcedPositive(flows.yield).orNullIfZero(),
                expense = DisplayAmount.forcedNegative(flows.expense),
                // An invoice payment has a leg outside this perimeter, so it *is* a
                // flow here — unlike a transfer, whose two legs are both inside.
                invoicePayment = DisplayAmount.forcedNegative(liabilityMonthFlows(month).payment)
                    .orNullIfZero(),
                adjustment = DisplayAmount.explicitSign(flows.adjustment).orNullIfZero(),
                finalBalance = DisplayAmount.natural(naturalBalanceUpTo(month, AccountType.ASSET)),
            )
        }

        // No yield line here: the perimeter is LIABILITY, where there is nothing to
        // segregate, and the scope already speaks its own vocabulary (design D7).
        TransactionScope.CARDS -> {
            val flows = liabilityMonthFlows(month)
            // The ledger's own sign, so the column reads like a statement and still
            // closes: opening − spending + payments + adjustment.
            BalanceOverview.Cards(
                openingBalance = DisplayAmount.owed(naturalBalanceUpTo(previous, AccountType.LIABILITY)),
                expense = DisplayAmount.forcedNegative(flows.expense),
                payment = DisplayAmount.forcedPositive(flows.payment).orNullIfZero(),
                adjustment = DisplayAmount.explicitSign(flows.adjustment).orNullIfZero(),
                finalBalance = DisplayAmount.owed(naturalBalanceUpTo(month, AccountType.LIABILITY)),
            )
        }

        TransactionScope.ALL -> {
            val asset = assetMonthFlows(month, yieldDimensionId)
            val liability = liabilityMonthFlows(month)
            BalanceOverview.Overall(
                openingNet = DisplayAmount.natural(netBalanceUpTo(previous)),
                income = DisplayAmount.forcedPositive(asset.income),
                yield = DisplayAmount.forcedPositive(asset.yield).orNullIfZero(),
                // Disjoint sets — a card purchase has no ASSET leg — so aggregating
                // them cannot double-count. Which book the money left is the scope's
                // question, not the summary's.
                expense = DisplayAmount.forcedNegative(asset.expense + liability.expense),
                // Both legs are inside this perimeter: shown because the user asks for
                // it, outside the sum because it moves nothing.
                invoicePayment = DisplayAmount.neutral(liability.payment).orNullIfZero(),
                adjustment = DisplayAmount.explicitSign(asset.adjustment + liability.adjustment)
                    .orNullIfZero(),
                finalNet = DisplayAmount.natural(netBalanceUpTo(month)),
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
