package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.usecase.ConsolidateFigureUseCase
import com.neoutils.finsight.domain.usecase.consolidationDateOf
import com.neoutils.finsight.extension.DisplayAmount.SignPolicy
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
    base: String,
    today: LocalDate,
    consolidateFigure: ConsolidateFigureUseCase,
): BalanceOverview {
    val previous = month.minusMonth()

    // Every figure here aggregates across accounts, so its currency is not the currency of
    // any one of them: the ledger answers per currency and the consolidation layer is the one
    // reduction to what the card shows. One date for the whole card — a summary explained by
    // two quotes would be two summaries.
    val date = consolidationDateOf(month, today)
    suspend fun CurrencyBalance.figure(policy: SignPolicy) =
        consolidateFigure(balance = this, base = base, date = date, policy = policy).figure

    suspend fun CurrencyBalance.figureOrNull(policy: SignPolicy) =
        if (movedNothing()) null else figure(policy)

    return when (scope) {
        TransactionScope.ACCOUNTS -> {
            val flows = assetMonthFlows(month)
            BalanceOverview.Accounts(
                openingBalance = (naturalBalanceUpTo(previous, AccountType.ASSET)).figure(SignPolicy.NATURAL),
                income = (flows.income).figure(SignPolicy.FORCED_POSITIVE),
                expense = (flows.expense).figure(SignPolicy.FORCED_NEGATIVE),
                // An invoice payment has a leg outside this perimeter, so it *is* a
                // flow here — unlike a transfer, whose two legs are both inside.
                invoicePayment = (liabilityMonthFlows(month).payment).figureOrNull(SignPolicy.FORCED_NEGATIVE),
                adjustment = (flows.adjustment).figureOrNull(SignPolicy.EXPLICIT_SIGN),
                finalBalance = (naturalBalanceUpTo(month, AccountType.ASSET)).figure(SignPolicy.NATURAL),
            )
        }

        TransactionScope.CARDS -> {
            val flows = liabilityMonthFlows(month)
            // The ledger's own sign, so the column reads like a statement and still
            // closes: opening − spending + payments + adjustment.
            BalanceOverview.Cards(
                openingBalance = (naturalBalanceUpTo(previous, AccountType.LIABILITY)).figure(SignPolicy.OWED),
                expense = (flows.expense).figure(SignPolicy.FORCED_NEGATIVE),
                payment = (flows.payment).figureOrNull(SignPolicy.FORCED_POSITIVE),
                adjustment = (flows.adjustment).figureOrNull(SignPolicy.EXPLICIT_SIGN),
                finalBalance = (naturalBalanceUpTo(month, AccountType.LIABILITY)).figure(SignPolicy.OWED),
            )
        }

        TransactionScope.ALL -> {
            val asset = assetMonthFlows(month)
            val liability = liabilityMonthFlows(month)
            BalanceOverview.Overall(
                openingNet = (netBalanceUpTo(previous)).figure(SignPolicy.NATURAL),
                income = (asset.income).figure(SignPolicy.FORCED_POSITIVE),
                // Disjoint sets — a card purchase has no ASSET leg — so aggregating
                // them cannot double-count. Which book the money left is the scope's
                // question, not the summary's.
                expense = ((asset.expense + liability.expense)).figure(SignPolicy.FORCED_NEGATIVE),
                // Both legs are inside this perimeter: shown because the user asks for
                // it, outside the sum because it moves nothing.
                invoicePayment = (liability.payment).figureOrNull(SignPolicy.NEUTRAL),
                adjustment = ((asset.adjustment + liability.adjustment)).figureOrNull(SignPolicy.EXPLICIT_SIGN),
                finalNet = (netBalanceUpTo(month)).figure(SignPolicy.NATURAL),
            )
        }
    }
}

/**
 * The consolidated figure is the **sum** of the two natures, not an aggregate of its
 * own: liabilities are stored in credit, so no sign rule is needed here either.
 */
private suspend fun IEntryRepository.netBalanceUpTo(month: YearMonth): CurrencyBalance =
    naturalBalanceUpTo(month, AccountType.ASSET) + naturalBalanceUpTo(month, AccountType.LIABILITY)

/**
 * A flow the month does not have is an absent line, not a zero the card must hide. It asks
 * the per-currency result and not the figure: a line exists because the ledger moved, which
 * is a fact no rate takes part in.
 */
private fun CurrencyBalance.movedNothing() = entries.values.all { it == 0.0 }
