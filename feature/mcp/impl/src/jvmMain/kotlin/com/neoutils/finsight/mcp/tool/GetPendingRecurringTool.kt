package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.safeOnDay
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentFigure
import com.neoutils.finsight.mcp.surface.AgentFigureLimitation
import com.neoutils.finsight.mcp.surface.AgentPendingRecurringAnswer
import com.neoutils.finsight.mcp.surface.AgentPeriod
import com.neoutils.finsight.mcp.surface.AgentPerimeter
import com.neoutils.finsight.mcp.surface.AgentRecurring
import com.neoutils.finsight.mcp.surface.agentFigure
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * **Which recurring cycles are still waiting** to be confirmed or skipped, and what confirming all
 * of them would cost.
 *
 * The total is money that has **not moved**. Nothing of a recurring is in the ledger until a cycle is
 * confirmed, so this figure answers "what is coming", never "what was spent" — and it is denominated
 * by the account or card each template posts through, which is why it comes back per currency like
 * any other figure that spans accounts.
 */
internal class GetPendingRecurringTool(
    private val clock: Clock,
    private val recurringRepository: IRecurringRepository,
    private val occurrenceRepository: IRecurringOccurrenceRepository,
    private val getPendingRecurring: GetPendingRecurringUseCase,
    private val consolidateMoney: ConsolidateMoneyUseCase,
) : McpTool {

    override val name: String = McpToolName.GET_PENDING_RECURRING.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "The recurring templates whose cycle for this month has come due and has neither been " +
            "confirmed nor skipped, with what confirming all of them would post. " +
            "PERIMETER: nothing here is in the ledger yet — these are intentions, not postings, " +
            "and `expected_total` must never be reported as money already spent. A template " +
            "whose day has not arrived yet is not pending and is not here, nor is an archived " +
            "one. " +
            "`as_of` moves the question to another date: a template is pending when its day of " +
            "the month has arrived on that date and its cycle for that month is unhandled."

    override val inputSchema = schema(
        "as_of" to text(
            "The date the question is asked on, as `2026-03-14`. Defaults to today. " +
                "It decides both which month's cycles are looked at and which days have come.",
        ),
    )

    override suspend fun call(arguments: JsonObject?) = reading {
        val today = clock.today()
        val asOf = arguments.date("as_of") ?: today
        val month = asOf.yearMonth

        val pending = getPendingRecurring(
            recurringList = recurringRepository.observeAllRecurring().first(),
            occurrences = occurrenceRepository.getAllOccurrences(),
            today = asOf,
        )

        answer(
            AgentPendingRecurringAnswer(
                period = AgentPeriod.of(month, today),
                pending = pending.map { it.toAgentRecurring(asOf) },
                expectedTotal = consolidateMoney.agentFigure(
                    // Each template's amount with its own currency, added per currency by the one
                    // implementation of that addition. Nothing is converted here; the reducer does
                    // that afterwards, once, as it does for every figure.
                    money = pending.fold(MoneyByCurrency.zero) { total, recurring ->
                        val currency = recurring.currency ?: return@fold total
                        total + MoneyByCurrency.of(currency, recurring.amount)
                    },
                    on = asOf,
                    policy = DisplayAmount::magnitude,
                ),
                perimeter = AgentPerimeter(
                    covers = "Recurring templates due on or before $asOf whose cycle for " +
                        "$month has not been confirmed or skipped.",
                    excludes = listOf(
                        "templates whose day of the month has not arrived yet",
                        "archived templates, which post nothing",
                        "cycles already confirmed — those are postings, and are in the month's " +
                            "figures like any other",
                        "templates pointing at no account or card, whose amount has no currency " +
                            "and so is in no total",
                    ),
                    seeAlso = listOf(McpToolName.GET_MONTH_SUMMARY.wireName),
                ),
            )
        )
    }

    /**
     * What denominates a template's amount: the account or the card it posts through (design D17).
     *
     * `null` when it points at neither, which happens when the account or card was deleted out from
     * under it. The amount is still known; what it is *in* is not, and guessing a currency here is
     * the silent decision the app removed everywhere else.
     */
    private val Recurring.currency: String?
        get() = account?.currency ?: creditCard?.currency

    private fun Recurring.toAgentRecurring(asOf: LocalDate) = AgentRecurring(
        id = id,
        type = type.name.lowercase(),
        title = label,
        amount = currency
            ?.let { AgentFigure.exact(amount, it) }
            ?: AgentFigure(
                amount = null,
                currency = null,
                byCurrency = emptyList(),
                isApproximate = false,
                limitation = AgentFigureLimitation(
                    missingRateFor = emptyList(),
                    explanation = "This template points at no account or card, so its amount has " +
                        "no currency and is in no total. It cannot post until it is pointed " +
                        "somewhere that exists.",
                ),
            ),
        dayOfMonth = dayOfMonth,
        category = category?.name,
        categoryId = category?.id,
        account = account?.name,
        accountId = account?.id,
        card = creditCard?.name,
        cardId = creditCard?.id,
        nextOccurrence = asOf.yearMonth.safeOnDay(dayOfMonth),
        isPending = true,
        isArchived = isArchived,
    )
}
