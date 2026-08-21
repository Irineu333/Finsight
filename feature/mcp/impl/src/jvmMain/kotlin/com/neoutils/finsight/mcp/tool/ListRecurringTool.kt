package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.extension.safeOnDay
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentFigure
import com.neoutils.finsight.mcp.surface.AgentFigureLimitation
import com.neoutils.finsight.mcp.surface.AgentPeriod
import com.neoutils.finsight.mcp.surface.AgentPerimeter
import com.neoutils.finsight.mcp.surface.AgentRecurring
import com.neoutils.finsight.mcp.surface.AgentRecurringListAnswer
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * **Every recurring template**, and whether this month's cycle is still waiting.
 *
 * Its recorte, against `get_pending_recurring`: this is the **catalogue** — all the templates,
 * including the ones whose day has not come and the ones already handled this month, which is what
 * an agent needs to name one or to see what the user has standing. That tool answers a narrower
 * question — only the cycles due and unhandled — and carries what confirming all of them would post.
 *
 * Nothing here is in the ledger. A template is what *would* be posted, and `is_pending` is the only
 * thing that distinguishes a cycle still waiting from one already dealt with. Whether a cycle is
 * pending is not decided here: `GetPendingRecurringUseCase` owns it, and both tools ask it.
 */
internal class ListRecurringTool(
    private val clock: Clock,
    private val recurringRepository: IRecurringRepository,
    private val occurrenceRepository: IRecurringOccurrenceRepository,
    private val getPendingRecurring: GetPendingRecurringUseCase,
) : McpTool {

    override val name: String = McpToolName.LIST_RECURRING.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "Every recurring template the user has, with what it would post, the day of the month it " +
            "falls on, where it posts to, and whether this month's cycle is still waiting to be " +
            "confirmed or skipped. " +
            "PERIMETER: this is the catalogue — templates whose day has not arrived yet and " +
            "templates already confirmed or skipped this month are both here, marked by " +
            "`is_pending`. For only the cycles that are due and unhandled, together with what " +
            "confirming all of them would post, use get_pending_recurring. " +
            "NOTHING here is in the ledger: an amount on a template is what WOULD be posted, and " +
            "must never be reported as money already spent."

    override val inputSchema = schema(
        "as_of" to text(
            "The date the question is asked on, as `2026-03-14`. Defaults to today. It decides " +
                "which month's cycles `is_pending` is about.",
        ),
        "include_archived" to yesOrNo(
            "Include templates the user has archived. Defaults to false — an archived template " +
                "posts nothing.",
        ),
    )

    override suspend fun call(arguments: JsonObject?) = reading {
        val today = clock.today()
        val asOf = arguments.date("as_of") ?: today
        val includeArchived = arguments.flag("include_archived", default = false)

        val all = recurringRepository.observeAllRecurring().first()
        // Whether a cycle is still waiting has one owner, and both tools of this family ask it
        // rather than each deciding what "pending" means.
        val pending = getPendingRecurring(
            recurringList = all,
            occurrences = occurrenceRepository.getAllOccurrences(),
            today = asOf,
        ).map { it.id }.toSet()

        val templates = all
            .filter { includeArchived || !it.isArchived }
            // Title, then the identity no two templates share: the same order in every call.
            .sortedWith(compareBy({ it.label.lowercase() }, { it.id }))

        answer(
            AgentRecurringListAnswer(
                period = AgentPeriod.of(asOf.yearMonth, today),
                recurring = templates.map { it.toAgentRecurring(asOf, it.id in pending) },
                perimeter = AgentPerimeter(
                    covers = "Every recurring template the filter matches, with whether its " +
                        "cycle for ${asOf.yearMonth} is still waiting on $asOf.",
                    excludes = listOfNotNull(
                        "the postings a confirmed cycle produced — those are in the ledger like " +
                            "any other, and list_transactions has them",
                        "archived templates".takeUnless { includeArchived },
                    ),
                    seeAlso = listOf(
                        McpToolName.GET_PENDING_RECURRING.wireName,
                        McpToolName.LIST_TRANSACTIONS.wireName,
                    ),
                ),
            ),
        )
    }

    /**
     * What denominates a template's amount: the account or the card it posts through.
     *
     * `null` when it points at neither, which happens when the account or card was deleted out from
     * under it. The amount is still known; what it is *in* is not, and guessing a currency here is
     * the silent decision the app removed everywhere else.
     */
    private val Recurring.currency: String?
        get() = account?.currency ?: creditCard?.currency

    private fun Recurring.toAgentRecurring(asOf: LocalDate, isPending: Boolean) = AgentRecurring(
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
        isPending = isPending,
        isArchived = isArchived,
    )
}
