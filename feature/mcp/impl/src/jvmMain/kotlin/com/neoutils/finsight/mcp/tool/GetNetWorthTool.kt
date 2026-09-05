package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentNetWorthAnswer
import com.neoutils.finsight.mcp.surface.AgentPeriod
import com.neoutils.finsight.mcp.surface.AgentPerimeter
import com.neoutils.finsight.mcp.surface.agentFigure
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * **What is owned less what is owed**, per currency and consolidated.
 *
 * The figure is `Σ ASSET + Σ LIABILITY` and the addition is not a slip: a liability sits in credit
 * in the ledger, so adding the two natures *is* the subtraction. The conversion accounts stay out,
 * which is what keeps an exchange from moving net worth by itself.
 *
 * It is the counterpart `get_balance` names: the same money, with the card debt taken off. Offering
 * one without the other is what makes the two indistinguishable, which is the failure the perimeter
 * requirement exists for.
 */
internal class GetNetWorthTool(
    private val clock: Clock,
    private val entryRepository: IEntryRepository,
    private val consolidateMoney: ConsolidateMoneyUseCase,
) : McpTool {

    override val name: String = McpToolName.GET_NET_WORTH.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "What the user is worth: everything in their accounts MINUS everything owed on their " +
            "credit cards, in one figure. " +
            "PERIMETER: assets and card debt together. This is the figure get_balance is NOT — " +
            "get_balance leaves card debt out. " +
            "With no month, it counts every posting on record, including ones dated in the " +
            "future: instalments already committed to a future invoice are already owed here. " +
            "Give a month to cut it at that month's end instead. " +
            "Across currencies it comes back decomposed per currency, with the consolidated " +
            "figure and the date of the rate."

    override val inputSchema = schema(
        "month" to text(
            "Cuts the figure at the end of this month, as `2026-03`. " +
                "Omit to count every posting on record, whatever its date.",
        ),
    )

    override suspend fun call(arguments: JsonObject?) = reading {
        val today = clock.today()
        val month = arguments.monthOrNull("month")

        val money = when (month) {
            // Every posting, whatever its date — the ledger's own all-time reading.
            null -> entryRepository.netWorthByCurrency()
            // Cut at a month end. The two natures are added, not subtracted: liabilities are
            // stored in credit, so `MoneyByCurrency.plus` is the whole rule, and it has one owner.
            else -> entryRepository.naturalBalanceUpToByCurrency(month, AccountType.ASSET) +
                entryRepository.naturalBalanceUpToByCurrency(month, AccountType.LIABILITY)
        }

        val on = month?.lastDay ?: today

        answer(
            AgentNetWorthAnswer(
                netWorth = consolidateMoney.agentFigure(
                    money = money,
                    on = on,
                    policy = DisplayAmount::natural,
                ),
                asOf = AgentPeriod.upTo(on, today, month?.toString()),
                perimeter = AgentPerimeter(
                    covers = when (month) {
                        null -> "Every account balance plus every credit-card balance, over every " +
                            "date on record — postings dated in the future included."

                        else -> "Every account balance plus every credit-card balance, counting " +
                            "postings dated on or before ${month.lastDay}."
                    },
                    excludes = listOf(
                        "the app's internal conversion accounts, so an exchange does not move " +
                            "this figure by itself",
                        "budgets, plans and recurring templates, which are intentions rather " +
                            "than postings",
                    ),
                    seeAlso = listOf(
                        McpToolName.GET_BALANCE.wireName,
                        McpToolName.GET_CARD_OVERVIEW.wireName,
                    ),
                ),
            )
        )
    }
}
