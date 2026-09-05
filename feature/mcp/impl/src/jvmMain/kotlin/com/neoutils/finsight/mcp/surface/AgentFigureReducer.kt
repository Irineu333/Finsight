package com.neoutils.finsight.mcp.surface

import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.datetime.LocalDate

/**
 * A per-currency ledger figure, as an agent receives it.
 *
 * **The reduction is not done here.** A rate multiplies money in exactly one place in this app, and
 * a second surface is not a reason for a second place: this consumes [ConsolidateMoneyUseCase] and
 * translates what comes back. A tool that summed two currencies, or reached for a rate of its own,
 * would produce a number nothing else in the app agrees with.
 *
 * **What it adds is the account of what the reduction could not do** (design D16). The reducer
 * answers a figure of one term when it reduced to one number and of several when the archive had no
 * rate for part of it; a payload that dropped the extra terms would look identical to a clean
 * consolidation. So the terms no rate reached become [AgentFigureLimitation], said in words, beside
 * a decomposition that is exact whatever happened.
 *
 * @param policy how the figure reads its own sign — the same argument the reducer demands, for the
 * same reason: whether this is a balance, a magnitude or a debt is the caller's to know, and a
 * figure carried without it is the failure `DisplayAmount` exists to prevent. It is applied to the
 * decomposition too, so [AgentFigure.byCurrency] and [AgentFigure.amount] cannot read in opposite
 * signs — a card debt reported as `-1200` beside `1200` is one figure contradicting itself.
 */
internal suspend fun ConsolidateMoneyUseCase.agentFigure(
    money: MoneyByCurrency,
    on: LocalDate,
    policy: (value: Double, currency: String, isApproximate: Boolean) -> DisplayAmount,
): AgentFigure {
    val figure = invoke(money, on, policy)
    val stated = figure.statedTerm()
    val unreached = figure.terms.filter { it !== stated }

    return AgentFigure(
        amount = stated?.value,
        currency = stated?.currency,
        // The ledger's own answer, not the reducer's terms: after a reduction the base term holds
        // several currencies added together, which is precisely what a decomposition must not do.
        byCurrency = money.toList().map {
            AgentMoney(
                currency = it.currency,
                amount = policy(it.value, it.currency, false).value,
            )
        },
        isApproximate = figure.isApproximate,
        rateDate = figure.asOf,
        limitation = unreached
            .takeIf { it.isNotEmpty() }
            ?.let { terms ->
                AgentFigureLimitation(
                    missingRateFor = terms.map { it.currency },
                    explanation = explanationOf(
                        missing = terms.map { it.currency },
                        on = on,
                        hasNumber = stated != null,
                    ),
                )
            },
    )
}

/**
 * A figure the reducer **already built against a nominated currency**, translated as it stands.
 *
 * The one case is a budget: its limit is denominated once, when the budget is created, and the
 * spending is re-expressed against *that* rather than against the base (design D13). What comes back
 * is already the reduction — `ReducedAmount.asFigure` — so there is nothing left to reduce and
 * nothing here reduces anything.
 *
 * **[AgentFigure.byCurrency] is the reduction's own terms here**, which is a different decomposition
 * from the one [agentFigure] publishes and is the right one for this figure: the part that reached
 * the limit's currency is a single term by construction, and each part no rate could price stands
 * beside it, exact, in its own currency. Publishing the raw ledger terms instead would decompose a
 * figure the user never reads that way.
 */
internal fun ConsolidatedAmount.agentFigure(): AgentFigure {
    val stated = statedTerm()
    val unreached = terms.filter { it !== stated }

    return AgentFigure(
        amount = stated?.value,
        currency = stated?.currency,
        byCurrency = terms.map { AgentMoney(currency = it.currency, amount = it.value) },
        isApproximate = isApproximate,
        rateDate = asOf,
        limitation = unreached
            .takeIf { it.isNotEmpty() }
            ?.let { unpriced ->
                AgentFigureLimitation(
                    missingRateFor = unpriced.map { it.currency },
                    explanation = explanationOf(
                        missing = unpriced.map { it.currency },
                        on = asOf,
                        hasNumber = stated != null,
                    ),
                )
            },
    )
}

/**
 * A single denominated amount the domain already resolved — a budget's limit, what is left of it —
 * as a figure of one term.
 *
 * It carries the approximation the amount carries rather than asserting exactness: a remainder
 * computed from spending a rate touched is approximate, and a figure that dropped that would read as
 * a number the app is sure of.
 */
internal fun DisplayAmount.agentFigure(): AgentFigure = AgentFigure(
    amount = value,
    currency = currency,
    byCurrency = listOf(AgentMoney(currency = currency, amount = value)),
    isApproximate = isApproximate,
)

/**
 * The term that answers for the figure as a number: the only one when the reduction produced one,
 * and otherwise the base term — the one everything that *could* be reduced was reduced into.
 *
 * `null` when several currencies are present and no rate reached any of them. There is no number
 * then, and naming one of the terms as the figure would be picking a currency by hand.
 */
private fun ConsolidatedAmount.statedTerm(): DisplayAmount? =
    if (isSingleTerm) terms.single() else base

private fun explanationOf(
    missing: List<String>,
    on: LocalDate?,
    hasNumber: Boolean,
): String {
    val consequence =
        if (hasNumber) "`amount` covers only the part that could be converted"
        else "this figure has no single number"
    val asOfText = if (on == null) "on this figure's date" else "on $on"

    return "The local exchange-rate archive holds no rate $asOfText for " +
        "${missing.joinToString(", ")}, so $consequence. The parts are in `by_currency`, each " +
        "exact in its own currency. Report them as they are: adding them would require a rate " +
        "the app does not have."
}
