@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.mcp.contract.ArchivedScope
import com.neoutils.finsight.mcp.contract.AssumedDefaults
import com.neoutils.finsight.mcp.contract.Cursor
import com.neoutils.finsight.mcp.contract.DisplaySign
import com.neoutils.finsight.mcp.contract.McpTool
import com.neoutils.finsight.mcp.contract.MoneyAmount
import com.neoutils.finsight.mcp.contract.PageLimit
import com.neoutils.finsight.mcp.contract.ResponseLimits
import com.neoutils.finsight.mcp.contract.TOOL_NAME_PREFIX
import com.neoutils.finsight.mcp.contract.ToolAnnotations
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.resolvePageLimit
import com.neoutils.finsight.mcp.contract.toolOutcomeSchema
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.yearMonth
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The name of the recurring listing. */
const val LIST_RECURRING_TOOL: String = "${TOOL_NAME_PREFIX}list_recurring"

/**
 * The user's recurring templates, and which of them are waiting to be confirmed.
 *
 * Which templates are pending is `GetPendingRecurringUseCase`'s answer — the templates
 * whose day of the month has arrived and that have no occurrence recorded for it — and
 * this tool asks it rather than restating the comparison.
 *
 * **Read-only, and the delivery means it.** Confirming, skipping, stopping and
 * reactivating a template are lifecycle transitions, and no tool of this server offers
 * one. Confirming in particular needs the date of the occurrence, and the domain has no
 * implicit "today" for it.
 */
class ListRecurringTool(
    private val recurring: IRecurringRepository,
    private val occurrences: IRecurringOccurrenceRepository,
    private val pending: GetPendingRecurringUseCase,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : McpTool {

    override val name: String = LIST_RECURRING_TOOL

    override val title: String = "List recurring"

    override val description: String = """
        The user's recurring templates, with the day of the month each repeats on, where
        it posts, and whether it is **pending** — its day has arrived this month and no
        occurrence has been recorded for it yet.

        `amount` is a single amount denominated by the account or card the template posts
        to; a template pointing nowhere carries none. Spending reads negative, income
        positive.

        `hasUsableSource` is false when the account or card the template names was removed
        or archived: the template survives and cannot post until it points somewhere real.

        Stopped templates are left out unless asked for, and the scope applied comes back
        in `assumed`.

        No tool of this server confirms, skips, stops or reactivates a template.
    """.trimIndent()

    override val inputSchema: JsonObject = objectSchema {
        pagingProperties()
        archivedProperty()
        stringProperty("referenceDate", "The date pending is judged against. Defaults to today.")
    }

    override val outputSchema: JsonObject = toolOutcomeSchema(
        resultSchema = listingSchema(
            itemsName = "recurring",
            item = recurringSchema,
            description = "The templates that satisfy the filter.",
        ),
        errorCodes = CommonToolCodes.all +
            ResponseLimits.CODE_PAGE_LIMIT_ABOVE_CEILING +
            ResponseLimits.CODE_PAGE_LIMIT_NOT_POSITIVE +
            AssumedDefaults.CODE_NOT_A_CIVIL_DATE,
    )

    override val annotations: ToolAnnotations = ToolAnnotations(readOnlyHint = true)

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        val args = Arguments(arguments)
        val archived = args.enum("archived", ArchivedScope.entries.toTypedArray())
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
            archived = archived,
        )
        val today = assumed.referenceDate.value

        val all = recurring.observeAllRecurring().first()
        val recorded = occurrences.getAllOccurrences()
        val pendingIds = pending(all, recorded, today).map { it.id }.toSet()

        val visible = when (assumed.archived.value) {
            ArchivedScope.EXCLUDED -> all.filter { !it.isArchived }
            ArchivedScope.INCLUDED -> all
            ArchivedScope.ONLY -> all.filter { it.isArchived }
        }

        val page = paginate(visible, limit, cursor) { it.id.toString() }
        val occurrenceOfMonth = recorded
            .filter { it.yearMonth == today.yearMonth }
            .associateBy { it.recurringId }

        val items = page.items.map { template ->
            buildJsonObject {
                putRecurring(
                    recurring = template,
                    isPending = template.id in pendingIds,
                    thisMonth = occurrenceOfMonth[template.id],
                )
            }
        }

        return ok {
            putPage("recurring", page.with(items))
            putAssumed(assumed)
        }
    }
}

internal fun JsonObjectBuilder.putRecurring(
    recurring: Recurring,
    isPending: Boolean,
    thisMonth: RecurringOccurrence?,
) {
    put("id", recurring.id)
    put("type", recurring.type.name)
    recurring.title?.let { put("title", it) }
    put("label", recurring.label)
    put("dayOfMonth", recurring.dayOfMonth)
    put("isArchived", recurring.isArchived)
    put("hasUsableSource", recurring.hasUsableSource)
    put("isPending", isPending)

    recurring.account?.let { putAccountRef("account", it) }
    recurring.creditCard?.let { putRef("creditCard", it.id, it.name) }
    recurring.category?.let { putRef("category", it.id, it.name) }

    // A template posts through one account or one card, and the amount is denominated by
    // it. With neither — the source was removed — there is no currency, and an amount
    // without one would be a number the consumer would denominate by guessing.
    val currency = recurring.account?.currency ?: recurring.creditCard?.currency
    currency?.let {
        put("amount", ToolJson.encodeToJsonElement(MoneyAmount.of(recurring.displaySignedAmount(), it)))
    }

    thisMonth?.let { occurrence ->
        putJsonObject("thisMonth") {
            put("status", occurrence.status.name)
            put("cycleNumber", occurrence.cycleNumber)
            put("effectiveDate", occurrence.effectiveDate.toString())
            occurrence.transactionId?.let { put("transactionId", it) }
        }
    }
}

/**
 * The template's amount with the display sign of this surface.
 *
 * `Recurring.amount` is a magnitude and the direction is in `type`, which is the input
 * vocabulary the user picked. An expense template therefore reads negative and an income
 * one positive, by the nominal account each would post against — the same rule every other
 * figure of this surface reads with.
 */
private fun Recurring.displaySignedAmount(): Double = when (type) {
    TransactionType.EXPENSE -> DisplaySign.of(AccountType.EXPENSE) * amount
    TransactionType.INCOME -> DisplaySign.of(AccountType.INCOME) * -amount
    TransactionType.ADJUSTMENT -> amount
}

internal val recurringSchema: JsonObject = objectSchema(
    required = listOf("id", "type", "dayOfMonth", "isPending"),
) {
    integerProperty("id", "The opaque identifier of the template.")
    enumProperty("type", TransactionType.entries.map { it.name }, "The direction the user declared.")
    stringProperty("title", "What the user wrote, when they wrote anything.")
    stringProperty("label", "What the app shows for it — the title, or the category's name.")
    integerProperty("dayOfMonth", "The day it repeats on. A month too short falls back to its last day.")
    booleanProperty("isArchived", "A stopped template: it keeps its history and produces nothing new.")
    booleanProperty("hasUsableSource", "False when the account or card it posts through is gone or closed.")
    booleanProperty("isPending", "Its day has arrived this month and no occurrence is recorded for it.")
    objectProperty("account", refSchema("The account it posts through."))
    objectProperty("creditCard", refSchema("The card it posts through."))
    objectProperty("category", refSchema("The category it classifies in."))
    objectProperty("amount", moneyAmountSchema)
    objectProperty(
        name = "thisMonth",
        schema = objectSchema(required = listOf("status", "cycleNumber")) {
            enumProperty("status", listOf("CONFIRMED", "SKIPPED"), "What was recorded for the reference month.")
            integerProperty("cycleNumber", "Which cycle of the template it was.")
            stringProperty("effectiveDate", "The date the occurrence was recorded for.")
            integerProperty("transactionId", "The transaction a confirmation produced.")
        },
    )
}
