@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.mcp.contract.AssumedDefaults
import com.neoutils.finsight.mcp.contract.Cursor
import com.neoutils.finsight.mcp.contract.McpTool
import com.neoutils.finsight.mcp.contract.MoneyAmount
import com.neoutils.finsight.mcp.contract.PageLimit
import com.neoutils.finsight.mcp.contract.ResponseLimits
import com.neoutils.finsight.mcp.contract.TOOL_NAME_PREFIX
import com.neoutils.finsight.mcp.contract.ToolAnnotations
import com.neoutils.finsight.mcp.contract.ToolError
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.resolvePageLimit
import com.neoutils.finsight.mcp.contract.toolOutcomeSchema
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The name of the invoice listing, named by the overview and the write path. */
const val LIST_INVOICES_TOOL: String = "${TOOL_NAME_PREFIX}list_invoices"

/**
 * The bills of the user's cards, each with what it owes.
 *
 * What an invoice owes is `CalculateInvoiceUseCase`'s answer and nothing else — `Σ` the
 * entries carrying its dimension, read positive — and it is a **scalar** because the card
 * facade guarantees that an invoice's dimension only ever lands on the one `LIABILITY`
 * account of its card, whose currency is immutable. That guarantee lives in the use case;
 * this tool consumes it rather than restating it.
 *
 * **Lifecycle is out of reach here.** Closing, paying and reopening a bill are not offered
 * by any tool of this delivery, deliberately.
 */
class ListInvoicesTool(
    private val invoices: IInvoiceRepository,
    private val creditCards: ICreditCardRepository,
    private val calculateInvoice: CalculateInvoiceUseCase,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : McpTool {

    override val name: String = LIST_INVOICES_TOOL

    override val title: String = "List invoices"

    override val description: String = """
        The bills of the user's credit cards, newest first, each with what it owes and the
        window of purchase dates it admits.

        `owed` is a single amount and not a per-currency collection: every leg of an
        invoice posts to the one account of its card, which declares one currency.

        An invoice has **three months and not one** — it opens on one, closes on the next
        and falls due on a third — so `openingDate`/`closingDate` are the days a purchase
        falls between to belong to it, and `dueDate` is when the bill is paid. To read one
        bill's transactions, pass its id to $LIST_TRANSACTIONS_TOOL as `invoiceId`.

        `openOnly` narrows to the bills currently taking new purchases.

        No tool of this server closes, pays or reopens a bill.
    """.trimIndent()

    override val inputSchema: JsonObject = objectSchema {
        pagingProperties()
        integerProperty("creditCardId", "Only this card's bills.")
        booleanProperty("openOnly", "Only the bills whose status is strictly OPEN.")
    }

    override val outputSchema: JsonObject = toolOutcomeSchema(
        resultSchema = listingSchema(
            itemsName = "invoices",
            item = invoiceSchema,
            description = "The bills that satisfy the filter, newest opening month first.",
        ),
        errorCodes = CommonToolCodes.all +
            ResponseLimits.CODE_PAGE_LIMIT_ABOVE_CEILING +
            ResponseLimits.CODE_PAGE_LIMIT_NOT_POSITIVE,
    )

    override val annotations: ToolAnnotations = ToolAnnotations(readOnlyHint = true)

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        val args = Arguments(arguments)
        val creditCardId = args.long("creditCardId")
        val openOnly = args.boolean("openOnly") ?: false
        val requestedLimit = args.int("limit")
        val cursor = args.string("cursor")?.let(::Cursor)
        args.failure?.let { return ToolOutcome.Failed(it) }

        val limit = when (val resolved = resolvePageLimit(requestedLimit)) {
            is PageLimit.Refused -> return ToolOutcome.Failed(resolved.error)
            is PageLimit.Accepted -> resolved.limit
        }

        creditCardId?.let { id ->
            creditCards.getCreditCardById(id) ?: return ToolOutcome.Failed(
                ToolError.notFound(CommonToolCodes.NOT_FOUND, "No credit card with id $id"),
            )
        }

        // Every cut is a read of the repository that owns it: there is one for a card's
        // bills, one for the bills that are open, and one for the open bill of a card.
        val matching = when {
            creditCardId != null && openOnly -> listOfNotNull(invoices.getOpenInvoice(creditCardId))
            creditCardId != null -> invoices.getInvoicesByCreditCard(creditCardId)
            openOnly -> invoices.getOpenInvoices()
            else -> invoices.getAllInvoices()
        }

        val page = paginate(matching, limit, cursor) { it.id.toString() }
        val items = page.items.map { invoice ->
            buildJsonObject { putInvoice(invoice, calculateInvoice(invoice)) }
        }

        return ok {
            putPage("invoices", page.with(items))
            putAssumed(
                AssumedDefaults.resolve(today = clock.today(timeZone), timeZone = timeZone),
            )
        }
    }
}

internal fun JsonObjectBuilder.putInvoice(invoice: Invoice, owed: Double) {
    put("id", invoice.id)
    putRef("creditCard", invoice.creditCard.id, invoice.creditCard.name)
    put("status", invoice.status.name)
    put("openingMonth", invoice.openingMonth.toString())
    put("closingMonth", invoice.closingMonth.toString())
    put("dueMonth", invoice.dueMonth.toString())
    put("openingDate", invoice.openingDate.toString())
    put("closingDate", invoice.closingDate.toString())
    put("dueDate", invoice.dueDate.toString())
    put("acceptsNewExpenses", !invoice.status.isClosedToNewExpenses)
    invoice.creditCard.currency?.let { currency ->
        put("owed", ToolJson.encodeToJsonElement(MoneyAmount.of(owed, currency)))
    }
}

internal val invoiceSchema: JsonObject = objectSchema(
    required = listOf("id", "creditCard", "status", "dueMonth"),
) {
    integerProperty("id", "The opaque identifier of the bill.")
    objectProperty("creditCard", refSchema("The card this bill belongs to."))
    enumProperty(
        name = "status",
        values = Invoice.Status.entries.map { it.name },
        description = "Where the bill is in its life. No tool of this server moves it.",
    )
    stringProperty("openingMonth", "YYYY-MM. The cycle opens on the card's closing day of this month.")
    stringProperty("closingMonth", "YYYY-MM. The cycle closes on the card's closing day of this month.")
    stringProperty("dueMonth", "YYYY-MM. The month the bill falls due.")
    stringProperty("openingDate", "The first purchase date this bill admits.")
    stringProperty("closingDate", "The first purchase date it no longer admits — it belongs to the next bill.")
    stringProperty("dueDate", "The day the bill falls due.")
    booleanProperty("acceptsNewExpenses", "False once the bill is closed or paid.")
    objectProperty("owed", moneyAmountSchema)
}
