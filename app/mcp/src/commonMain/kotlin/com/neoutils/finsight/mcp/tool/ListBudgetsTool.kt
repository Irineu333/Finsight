@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.mcp.contract.AssumedDefaults
import com.neoutils.finsight.mcp.contract.CivilDateRange
import com.neoutils.finsight.mcp.contract.Cursor
import com.neoutils.finsight.mcp.contract.McpTool
import com.neoutils.finsight.mcp.contract.MoneyAmount
import com.neoutils.finsight.mcp.contract.PageLimit
import com.neoutils.finsight.mcp.contract.ResponseLimits
import com.neoutils.finsight.mcp.contract.TOOL_NAME_PREFIX
import com.neoutils.finsight.mcp.contract.ToolAnnotations
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.ToolWarning
import com.neoutils.finsight.mcp.contract.ToolWarningCode
import com.neoutils.finsight.mcp.contract.resolvePageLimit
import com.neoutils.finsight.mcp.contract.toolOutcomeSchema
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.yearMonth
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The name of the budget listing. */
const val LIST_BUDGETS_TOOL: String = "${TOOL_NAME_PREFIX}list_budgets"

/**
 * The user's budgets and how far into each of them the month has gone.
 *
 * The progress is `CalculateBudgetProgressUseCase`'s answer, whole: the spending it
 * reports is reduced **to the budget's own currency and never to the base**, because a
 * limit is denominated once, when it is created, by the accounts the user actually
 * transacts in.
 *
 * **A budget declares its currency and has no default.** This listing therefore always
 * states it, and the write surface of this delivery does not create budgets at all.
 */
class ListBudgetsTool(
    private val budgets: IBudgetRepository,
    private val recurring: IRecurringRepository,
    private val transactions: ITransactionRepository,
    private val calculateProgress: CalculateBudgetProgressUseCase,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : McpTool {

    override val name: String = LIST_BUDGETS_TOOL

    override val title: String = "List budgets"

    override val description: String = """
        The user's budgets, each with the month's progress against its limit.

        **Every budget declares its own currency**, and the spending is compared in that
        currency — not in the base one. `limit`, `spent` and `remaining` are all
        denominated by the budget.

        `isResolved` is false when part of the spending sits in a currency the archive
        cannot price: `spent` is then a **floor** rather than a measurement, and reading it
        as a total would tell the user they have spent less than they have.

        The month is the month of the reference date, and the date assumed comes back in
        `assumed`.

        No tool of this server creates or changes a budget.
    """.trimIndent()

    override val inputSchema: JsonObject = objectSchema {
        pagingProperties()
        stringProperty("referenceDate", "The month this date falls in is the month measured. Defaults to today.")
    }

    override val outputSchema: JsonObject = toolOutcomeSchema(
        resultSchema = listingSchema(
            itemsName = "budgets",
            item = budgetSchema,
            description = "The user's budgets with the month's progress.",
        ),
        errorCodes = CommonToolCodes.all +
            ResponseLimits.CODE_PAGE_LIMIT_ABOVE_CEILING +
            ResponseLimits.CODE_PAGE_LIMIT_NOT_POSITIVE +
            AssumedDefaults.CODE_NOT_A_CIVIL_DATE,
    )

    override val annotations: ToolAnnotations = ToolAnnotations(readOnlyHint = true)

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        val args = Arguments(arguments)
        val referenceDate = args.date("referenceDate")
        val requestedLimit = args.int("limit")
        val cursor = args.string("cursor")?.let(::Cursor)
        args.failure?.let { return ToolOutcome.Failed(it) }

        val limit = when (val resolved = resolvePageLimit(requestedLimit)) {
            is PageLimit.Refused -> return ToolOutcome.Failed(resolved.error)
            is PageLimit.Accepted -> resolved.limit
        }

        val assumed = AssumedDefaults.resolve(
            today = clock.today(timeZone),
            timeZone = timeZone,
            referenceDate = referenceDate,
        )
        val month = assumed.referenceDate.value.yearMonth

        val all = budgets.getAllBudgets()
        val page = paginate(all, limit, cursor) { it.id.toString() }

        // A percentage limit is a share of the recurring **confirmed in that month**, so
        // the use case is handed the templates and the month's transactions rather than
        // being left to guess at either.
        val progress = calculateProgress(
            budgets = page.items,
            recurringList = recurring.observeAllRecurring().first(),
            transactions = transactions.getTransactionsBy(
                startDate = month.firstDay,
                endDate = month.lastDay,
            ),
            month = month,
        )

        val warnings = progress
            .filter { it.hasUnpricedSpending }
            .map { line ->
                ToolWarning(
                    code = ToolWarningCode.MISSING_EXCHANGE_RATE,
                    message = "Part of the spending of budget `${line.budget.title}` sits in a currency " +
                        "the archive cannot price at ${assumed.referenceDate.value}; `spent` is a floor.",
                    details = mapOf("budgetId" to line.budget.id.toString()),
                )
            }

        return ok(warnings = warnings) {
            putPage("budgets", page.with(progress.map { buildJsonObject { putBudget(it) } }))
            put("period", ToolJson.encodeToJsonElement(CivilDateRange(month.firstDay, month.lastDay)))
            putAssumed(assumed)
        }
    }
}

internal fun JsonObjectBuilder.putBudget(progress: BudgetProgress) {
    val budget = progress.budget
    put("id", budget.id)
    put("title", budget.title)
    put("currency", budget.currency)
    put("limitType", budget.limitType.name)
    budget.percentage?.let { put("percentage", it) }
    budget.recurringId?.let { put("recurringId", it) }
    put("limit", ToolJson.encodeToJsonElement(MoneyAmount.of(budget.amount, budget.currency)))
    put("spent", ToolJson.encodeToJsonElement(MoneyAmount.of(progress.spent, budget.currency)))
    put("remaining", ToolJson.encodeToJsonElement(MoneyAmount.of(progress.remaining, budget.currency)))
    put("isResolved", progress.isResolved)
    put("isExceeded", progress.isExceeded)
    put("isApproximate", progress.isApproximate)
    progress.progress?.let { put("progress", it) }
    putJsonArray("categories") {
        budget.categories.forEach { category ->
            add(buildJsonObject { putCategory(category) })
        }
    }
}

internal val budgetSchema: JsonObject = objectSchema(
    required = listOf("id", "title", "currency", "limit", "spent"),
) {
    integerProperty("id", "The opaque identifier of the budget.")
    stringProperty("title", "What the user calls this budget.")
    stringProperty("currency", "ISO 4217. Declared by the budget, never defaulted and never the base by assumption.")
    enumProperty("limitType", listOf("FIXED", "PERCENTAGE"), "How the limit is arrived at.")
    numberProperty("percentage", "The share of the recurring, on a PERCENTAGE limit.")
    integerProperty("recurringId", "The recurring a PERCENTAGE limit is a share of.")
    objectProperty("limit", moneyAmountSchema)
    objectProperty("spent", moneyAmountSchema)
    objectProperty("remaining", moneyAmountSchema)
    booleanProperty("isResolved", "False when `spent` is a floor: part of the spending could not be priced.")
    booleanProperty("isExceeded", "True only when the limit is *known* to be exceeded.")
    booleanProperty("isApproximate", "Whether reaching the budget's currency took a rate.")
    numberProperty("progress", "How full the bar is, 0..1. Absent when there is no answer.")
    arrayProperty("categories", categorySchema, "The categories this budget measures.")
}
