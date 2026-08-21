package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.usecase.CalculateReportStatsUseCase
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentPeriod
import com.neoutils.finsight.mcp.surface.AgentPerimeter
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.surface.AgentReportStatsAnswer
import com.neoutils.finsight.mcp.surface.agentFigure
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * **A period that is not a month**, seen from a perimeter the caller chooses.
 *
 * Its recorte, against `get_month_summary`: that one answers a calendar month across everything;
 * this one answers any range of dates, seen from a chosen set of accounts or from one card. The
 * perspective is what makes it a different question — money moving between two accounts *inside* the
 * chosen set is not a flow of that set, and the ledger's aggregate already leaves it out.
 */
internal class GetReportStatsTool(
    private val clock: Clock,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val calculateReportStats: CalculateReportStatsUseCase,
    private val consolidateMoney: ConsolidateMoneyUseCase,
) : McpTool {

    override val name: String = McpToolName.GET_REPORT_STATS.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "Income, spending, the net movement and the opening position over any range of dates, " +
            "seen from a chosen set of accounts or from one credit card. " +
            "PERIMETER: money is counted as it crosses the boundary of the chosen set. A " +
            "transfer whose two legs are both inside it moves nothing and is left out; one with " +
            "a leg outside it counts. A credit-card payment therefore DOES count as an outflow " +
            "here — the card sits outside an account perimeter — which is the one place this " +
            "differs from get_month_summary, where the same payment is reported separately and " +
            "left outside every total. With no accounts named the perimeter is every account, " +
            "archived ones included, so no history is silently dropped. " +
            "Use this for a period that is not a calendar month, or for a perimeter narrower " +
            "than the whole app; for a plain month across everything, get_month_summary is the " +
            "cheaper call."

    override val inputSchema = schema(
        "from" to text("First day of the range, as `2026-01-01`. Required."),
        "to" to text("Last day of the range, as `2026-03-31`. Required."),
        "account_ids" to numbers(
            "The accounts the figures are seen from. Omit for every account, archived included. " +
                "Ignored when `card_id` is given.",
        ),
        "card_id" to number(
            "Sees the figures from one credit card instead of from accounts.",
        ),
        required = listOf("from", "to"),
    )

    override suspend fun call(arguments: JsonObject?) = reading {
        val from = arguments.date("from")
            ?: return@reading refused(AgentRefusal(reason = "`from` is required, as `2026-01-01`."))
        val to = arguments.date("to")
            ?: return@reading refused(AgentRefusal(reason = "`to` is required, as `2026-03-31`."))
        if (to < from) {
            return@reading refused(
                AgentRefusal(reason = "`to` ($to) is before `from` ($from); the range is empty."),
            )
        }

        val cardId = arguments.long("card_id")
        val accountIds = arguments.longs("account_ids").orEmpty()

        val perspective: ReportPerspective
        val scope: String
        val scopeNames: List<String>

        if (cardId != null) {
            val card = creditCardRepository.getCreditCardById(cardId)
                ?: return@reading refused(AgentRefusal.notFound("credit card", cardId))
            perspective = ReportPerspective.CreditCardPerspective(cardId)
            scope = "card"
            scopeNames = listOf(card.name)
        } else {
            val accounts = accountRepository.getAllAccountsIncludingClosed()
            accountIds.firstOrNull { id -> accounts.none { it.id == id } }?.let {
                return@reading refused(AgentRefusal.notFound("account", it))
            }
            perspective = ReportPerspective.AccountPerspective(accountIds)
            scope = "accounts"
            scopeNames = accounts
                .filter { accountIds.isEmpty() || it.id in accountIds }
                .map { it.name }
        }

        val stats = calculateReportStats(perspective, from, to)

        answer(
            AgentReportStatsAnswer(
                period = AgentPeriod.range(from = from, to = to, today = clock.today()),
                scope = scope,
                scopeNames = scopeNames,
                openingBalance = consolidateMoney.agentFigure(stats.openingBalance, to, DisplayAmount::natural),
                income = consolidateMoney.agentFigure(stats.income, to, DisplayAmount::magnitude),
                expense = consolidateMoney.agentFigure(stats.expense, to, DisplayAmount::magnitude),
                balance = consolidateMoney.agentFigure(stats.balance, to, DisplayAmount::natural),
                perimeter = AgentPerimeter(
                    covers = "Postings dated between $from and $to that touch " +
                        when {
                            scope == "card" -> "the card ${scopeNames.single()}."
                            accountIds.isEmpty() -> "any of the user's accounts, archived ones " +
                                "included. A credit-card payment counts as an outflow here, " +
                                "because the card sits outside this perimeter."

                            else -> "the accounts named: ${scopeNames.joinToString(", ")}. A " +
                                "credit-card payment counts as an outflow here, because the card " +
                                "sits outside this perimeter."
                        },
                    excludes = listOf(
                        "transfers whose two legs are both inside this perimeter — they move " +
                            "nothing in or out of it",
                    ),
                    seeAlso = listOf(McpToolName.GET_MONTH_SUMMARY.wireName),
                ),
            )
        )
    }
}
