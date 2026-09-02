package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.ui.screen.transactions.TransactionsUiState.BalanceOverview
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth

/**
 * The summary of one perimeter, read from the ledger — never summed over the list the
 * screen happens to have loaded. The three scopes share one grammar: the opening and
 * closing figures are the accumulated balance of the perimeter's nature, and the flow
 * lines partition the perimeter's entries in the month. A movement whose legs all fall
 * inside the perimeter contributes zero by `Σ = 0`, so it needs no exception of its own.
 *
 * **Every figure here spans accounts**, so every one of them is a *consolidated* figure
 * and leaves through the reducer — never denominated by hand. The base currency is the
 * reducer's to say, not this file's: a summary line tagged with the base at the point of
 * formatting is indistinguishable, for a user whose accounts are all in the base, from
 * one that was properly reduced (design D29).
 *
 * What goes in is what the ledger answered, per currency; what comes out carries
 * currency, exactness and — where a rate is missing — more than one term.
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
 *
 * @param consolidate the one reducer; the figures of a month are consolidated at that
 * month's rates, and the opening ones at the previous month's, or the past would move on
 * its own whenever a rate changed.
 */
internal suspend fun IEntryRepository.balanceOverview(
    scope: TransactionScope,
    month: YearMonth,
    consolidate: ConsolidateMoneyUseCase,
    yieldDimensionId: Long? = null,
): BalanceOverview {
    val previous = month.minusMonth()

    suspend fun figure(
        money: MoneyByCurrency,
        on: LocalDate,
        policy: (Double, String, Boolean) -> DisplayAmount,
    ) = consolidate(money, on, policy)

    return when (scope) {
        TransactionScope.ACCOUNTS -> {
            val flows = assetMonthFlowsByCurrency(month, yieldDimensionId)
            BalanceOverview.Accounts(
                openingBalance = figure(
                    naturalBalanceUpToByCurrency(previous, AccountType.ASSET),
                    previous.lastDay,
                    DisplayAmount::natural,
                ),
                income = figure(flows.income, month.lastDay, DisplayAmount::forcedPositive),
                yield = figure(flows.yield, month.lastDay, DisplayAmount::forcedPositive)
                    .orNullIfZero(),
                expense = figure(flows.expense, month.lastDay, DisplayAmount::forcedNegative),
                // An invoice payment has a leg outside this perimeter, so it *is* a
                // flow here — unlike a transfer, whose two legs are both inside.
                invoicePayment = figure(
                    liabilityMonthFlowsByCurrency(month).payment,
                    month.lastDay,
                    DisplayAmount::forcedNegative,
                ).orNullIfZero(),
                adjustment = figure(
                    flows.adjustment,
                    month.lastDay,
                    DisplayAmount::explicitSign,
                ).orNullIfZero(),
                finalBalance = figure(
                    naturalBalanceUpToByCurrency(month, AccountType.ASSET),
                    month.lastDay,
                    DisplayAmount::natural,
                ),
            )
        }

        // No yield line here: the perimeter is LIABILITY, where there is nothing to
        // segregate, and the scope already speaks its own vocabulary (design D7).
        TransactionScope.CARDS -> {
            val flows = liabilityMonthFlowsByCurrency(month)
            // The ledger's own sign, so the column reads like a statement and still
            // closes: opening − spending + payments + adjustment.
            BalanceOverview.Cards(
                openingBalance = figure(
                    naturalBalanceUpToByCurrency(previous, AccountType.LIABILITY),
                    previous.lastDay,
                    DisplayAmount::owed,
                ),
                expense = figure(flows.expense, month.lastDay, DisplayAmount::forcedNegative),
                payment = figure(
                    flows.payment,
                    month.lastDay,
                    DisplayAmount::forcedPositive,
                ).orNullIfZero(),
                adjustment = figure(
                    flows.adjustment,
                    month.lastDay,
                    DisplayAmount::explicitSign,
                ).orNullIfZero(),
                finalBalance = figure(
                    naturalBalanceUpToByCurrency(month, AccountType.LIABILITY),
                    month.lastDay,
                    DisplayAmount::owed,
                ),
            )
        }

        TransactionScope.ALL -> {
            val asset = assetMonthFlowsByCurrency(month, yieldDimensionId)
            val liability = liabilityMonthFlowsByCurrency(month)
            BalanceOverview.Overall(
                openingNet = figure(
                    netBalanceUpTo(previous),
                    previous.lastDay,
                    DisplayAmount::natural,
                ),
                income = figure(asset.income, month.lastDay, DisplayAmount::forcedPositive),
                yield = figure(asset.yield, month.lastDay, DisplayAmount::forcedPositive)
                    .orNullIfZero(),
                // Disjoint sets — a card purchase has no ASSET leg — so aggregating
                // them cannot double-count. Which book the money left is the scope's
                // question, not the summary's.
                expense = figure(
                    asset.expense + liability.expense,
                    month.lastDay,
                    DisplayAmount::forcedNegative,
                ),
                // Both legs are inside this perimeter: shown because the user asks for
                // it, outside the sum because it moves nothing.
                invoicePayment = figure(
                    liability.payment,
                    month.lastDay,
                    DisplayAmount::neutral,
                ).orNullIfZero(),
                adjustment = figure(
                    asset.adjustment + liability.adjustment,
                    month.lastDay,
                    DisplayAmount::explicitSign,
                ).orNullIfZero(),
                finalNet = figure(netBalanceUpTo(month), month.lastDay, DisplayAmount::natural),
            )
        }
    }
}

/**
 * The consolidated figure is the **sum** of the two natures, not an aggregate of its
 * own: liabilities are stored in credit, so no sign rule is needed here either.
 */
private suspend fun IEntryRepository.netBalanceUpTo(month: YearMonth): MoneyByCurrency =
    naturalBalanceUpToByCurrency(month, AccountType.ASSET) +
        naturalBalanceUpToByCurrency(month, AccountType.LIABILITY)

/**
 * A flow the month does not have is an absent line, not a zero the card must hide — and
 * a figure is only absent when *every* term of it is zero: one term at zero beside
 * another that is not is still a movement.
 */
private fun ConsolidatedAmount.orNullIfZero(): ConsolidatedAmount? = takeIf { !it.isZero }
