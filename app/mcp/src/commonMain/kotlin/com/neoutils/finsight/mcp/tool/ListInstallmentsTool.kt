@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.mcp.contract.AssumedDefaults
import com.neoutils.finsight.mcp.contract.Cursor
import com.neoutils.finsight.mcp.contract.McpTool
import com.neoutils.finsight.mcp.contract.MoneyAmount
import com.neoutils.finsight.mcp.contract.PageLimit
import com.neoutils.finsight.mcp.contract.ResponseLimits
import com.neoutils.finsight.mcp.contract.TOOL_NAME_PREFIX
import com.neoutils.finsight.mcp.contract.ToolAnnotations
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.resolvePageLimit
import com.neoutils.finsight.mcp.contract.toolOutcomeSchema
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The name of the installment listing. */
const val LIST_INSTALLMENTS_TOOL: String = "${TOOL_NAME_PREFIX}list_installments"

/**
 * The user's installment plans, each with the transactions it produced.
 *
 * A plan is one decision that wrote N transactions, so the transactions are what says how
 * far it has gone and what currency it is in — `Installment` itself carries a count and a
 * total and nothing else. They are reached by the link a transaction keeps to the plan
 * that produced it, which is the same grouping the app's own installments screen does.
 */
class ListInstallmentsTool(
    private val installments: IInstallmentRepository,
    private val transactions: ITransactionRepository,
    private val accounts: IAccountRepository,
    private val creditCards: ICreditCardRepository,
    private val invoices: IInvoiceRepository,
    private val categories: ICategoryRepository,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : McpTool {

    override val name: String = LIST_INSTALLMENTS_TOOL

    override val title: String = "List installments"

    override val description: String = """
        The user's installment plans: how many payments each has, the total, and every
        transaction it produced with the bill that transaction landed in.

        A plan's money is denominated by the card its payments post to, so `total` is a
        single amount; a plan whose transactions are all gone carries none.

        `paidCount` counts the payments already billed in a closed or paid invoice;
        `isActive` says whether any payment is still to come.

        A card purchase in instalments is **one** operation that writes every payment at
        once — it is never one call per payment.
    """.trimIndent()

    override val inputSchema: JsonObject = objectSchema {
        pagingProperties()
        booleanProperty("activeOnly", "Only the plans with a payment still to be billed.")
    }

    override val outputSchema: JsonObject = toolOutcomeSchema(
        resultSchema = listingSchema(
            itemsName = "installments",
            item = installmentSchema,
            description = "The installment plans, newest first.",
        ),
        errorCodes = CommonToolCodes.all +
            ResponseLimits.CODE_PAGE_LIMIT_ABOVE_CEILING +
            ResponseLimits.CODE_PAGE_LIMIT_NOT_POSITIVE,
    )

    override val annotations: ToolAnnotations = ToolAnnotations(readOnlyHint = true)

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        val args = Arguments(arguments)
        val activeOnly = args.boolean("activeOnly") ?: false
        val requestedLimit = args.int("limit")
        val cursor = args.string("cursor")?.let(::Cursor)
        args.failure?.let { return ToolOutcome.Failed(it) }

        val limit = when (val resolved = resolvePageLimit(requestedLimit)) {
            is PageLimit.Refused -> return ToolOutcome.Failed(resolved.error)
            is PageLimit.Accepted -> resolved.limit
        }

        val byPlan = transactions.getAllTransactions()
            .filter { it.installmentId != null }
            .groupBy { checkNotNull(it.installmentId) }

        val context = TransactionContext.of(accounts, creditCards, invoices, categories)

        val all = installments.getAllInstallments()
            .sortedByDescending { it.id }
            .filter { plan -> !activeOnly || context.billedCount(byPlan[plan.id].orEmpty()) < plan.count }

        val page = paginate(all, limit, cursor) { it.id.toString() }
        val items = page.items.map { plan ->
            buildJsonObject { putInstallment(plan, byPlan[plan.id].orEmpty(), context) }
        }

        return ok {
            putPage("installments", page.with(items))
            putAssumed(AssumedDefaults.resolve(today = clock.today(timeZone), timeZone = timeZone))
        }
    }
}

/**
 * How many of a plan's payments are already behind the user.
 *
 * The answer is the invoice's own status and not a date comparison: a payment billed in a
 * closed or paid invoice has been charged, and one sitting in a bill still open has not.
 */
internal fun TransactionContext.billedCount(payments: List<Transaction>): Int = payments.count { payment ->
    invoice(payment.liabilityDimensionId)?.status?.isClosedToNewExpenses == true
}

internal fun JsonObjectBuilder.putInstallment(
    installment: Installment,
    payments: List<Transaction>,
    context: TransactionContext,
) {
    val ordered = payments.sortedBy { it.installmentNumber ?: Int.MAX_VALUE }
    val card = ordered.firstNotNullOfOrNull { context.card(it.liabilityAccountId) }
    val billed = context.billedCount(ordered)

    put("id", installment.id)
    put("count", installment.count)
    put("paidCount", billed)
    put("isActive", billed < installment.count)
    card?.let { putRef("creditCard", it.id, it.name) }
    card?.currency?.let { currency ->
        put("total", ToolJson.encodeToJsonElement(MoneyAmount.of(-installment.totalAmount, currency)))
    }
    putJsonArray("payments") {
        ordered.forEach { payment ->
            add(buildJsonObject { putTransaction(payment, context) })
        }
    }
}

internal val installmentSchema: JsonObject = objectSchema(required = listOf("id", "count")) {
    integerProperty("id", "The opaque identifier of the plan.")
    integerProperty("count", "How many payments the plan has.")
    integerProperty("paidCount", "How many of them are already billed in a closed or paid invoice.")
    booleanProperty("isActive", "Whether a payment is still to come.")
    objectProperty("creditCard", refSchema("The card the payments post to."))
    objectProperty("total", moneyAmountSchema)
    arrayProperty("payments", transactionSchema, "Every transaction the plan produced, in order.")
}
