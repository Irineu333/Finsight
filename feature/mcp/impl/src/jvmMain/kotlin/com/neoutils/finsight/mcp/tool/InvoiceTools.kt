package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.window
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentFigure
import com.neoutils.finsight.mcp.surface.AgentInvoice
import com.neoutils.finsight.mcp.surface.AgentInvoiceDetailAnswer
import com.neoutils.finsight.mcp.surface.AgentInvoiceListAnswer
import com.neoutils.finsight.mcp.surface.AgentPeriod
import com.neoutils.finsight.mcp.surface.AgentPerimeter
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.surface.agentFigure
import com.neoutils.finsight.mcp.surface.toAgentTransaction
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import com.neoutils.finsight.ui.model.TransactionPerspective
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * **A card's invoices over time** — the cycles themselves, whatever state each is in.
 *
 * Its recorte, and the whole reason it is a different tool from `get_card_overview`: that one
 * answers for the **card** — one line per card, its limit, and the single invoice standing open at
 * this moment. This one answers for the **invoices** — every cycle of a card, past, present and
 * future, closed, paid or still to open, in pages. Choosing between them is a question about which
 * of the two shapes is wanted, and neither description makes anybody call both to find out.
 *
 * What each one owes is a ledger read of its dimension, and **N invoices cost one read, not N**:
 * `owedByDimensionByCurrency` is the batched form, and using the per-invoice one in a loop here
 * would be the exact query storm it exists to prevent.
 */
internal class ListInvoicesTool(
    private val clock: Clock,
    private val invoiceRepository: IInvoiceRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val entryRepository: IEntryRepository,
    private val consolidateMoney: ConsolidateMoneyUseCase,
) : McpTool {

    override val name: String = McpToolName.LIST_INVOICES.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "The invoices of a credit card over time — every cycle, in any state — with the window " +
            "each covers, where it is in its life, and what it still owes. " +
            "PERIMETER: this answers for INVOICES, one line per cycle, and reaches future, open, " +
            "closed, paid and retroactive ones alike. It is the counterpart of get_card_overview, " +
            "which answers for CARDS instead: one line per card, its limit, and only the single " +
            "invoice open at this moment. Use this one for a card's history of cycles or for any " +
            "invoice that is not the open one; use that one for limits and for what is open now. " +
            "`owed_total` covers every invoice the filter matches, not just this page, and comes " +
            "from the ledger. Each invoice's own figure is exact, in its card's currency."

    override val inputSchema = schema(
        "card_id" to number("Only this card's invoices. Omit for every card's."),
        "status" to choice("Only invoices in this state.", STATUSES.keys.toList()),
        "limit" to number("How many invoices to return. Defaults to $DEFAULT_LIMIT, at most $MAX_LIMIT."),
        "offset" to number(
            "How many to skip. The cut is `limit` wide, so the page after this one starts at " +
                "`offset + limit`, and `has_more` says whether there is one.",
        ),
        "include_archived_cards" to yesOrNo(
            "Include invoices of cards the user has archived. Defaults to true, because their " +
                "history is still the user's.",
        ),
    )

    override suspend fun call(arguments: JsonObject?) = reading {
        val cardId = arguments.long("card_id")
        val status = arguments.oneOf("status", STATUSES.keys.toList())?.let { STATUSES.getValue(it) }
        val offset = arguments.count("offset", default = 0, max = MAX_OFFSET)
        val limit = arguments.count("limit", default = DEFAULT_LIMIT, max = MAX_LIMIT, min = 1)
        val includeArchivedCards = arguments.flag("include_archived_cards", default = true)

        // Asked before the invoices are read, because an empty list is the honest answer for a card
        // with no cycle yet and it is also what a wrong identifier produces. The two must not look
        // alike: one is a fact about the card, the other is a mistake the agent has to correct.
        if (cardId != null && creditCardRepository.getCreditCardById(cardId) == null) {
            return@reading refused(AgentRefusal.notFound("credit card", cardId))
        }

        val matching = when (cardId) {
            null -> invoiceRepository.getAllInvoices()
            else -> invoiceRepository.getInvoicesByCreditCard(cardId)
        }
            .filter { status == null || it.status == status }
            .filter { includeArchivedCards || !it.creditCard.isArchived }
            // Newest cycle first, with the identity breaking the tie: two invoices can fall due in
            // the same month, and an unstable order pages badly.
            .sortedWith(compareByDescending<Invoice> { it.dueDate }.thenByDescending { it.id })

        // One read for every invoice on the list, not one per invoice.
        val owed = entryRepository.owedByDimensionByCurrency(matching.mapNotNull { it.dimensionId })
        val page = matching.pageOf(offset = offset, limit = limit)

        answer(
            AgentInvoiceListAnswer(
                matching = page.matching,
                returned = page.returned,
                offset = page.offset,
                hasMore = page.hasMore,
                invoices = page.items.map { it.toAgentInvoice(owed.moneyOf(it)) },
                owedTotal = consolidateMoney.agentFigure(
                    // Every matching invoice, not the page: a total that moved with the page would
                    // be a different number for the same question asked twice.
                    money = matching.fold(MoneyByCurrency.zero) { total, invoice ->
                        total + owed.moneyOf(invoice)
                    },
                    on = clock.today(),
                    policy = DisplayAmount::natural,
                ),
                perimeter = AgentPerimeter(
                    covers = "Every invoice the filter matches, with what the ledger says is " +
                        "still owed on each.",
                    excludes = listOfNotNull(
                        "credit limits and how much of one is left — those belong to the card, " +
                            "and get_card_overview answers them",
                        "the postings that make up an invoice — get_invoice carries the statement",
                        "invoices of archived cards".takeUnless { includeArchivedCards },
                        "anything a page after this one holds — `has_more` says whether there is one",
                    ),
                    seeAlso = listOf(
                        McpToolName.GET_INVOICE.wireName,
                        McpToolName.GET_CARD_OVERVIEW.wireName,
                    ),
                ),
            ),
        )
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 200
        const val MAX_OFFSET = 10_000
    }
}

/**
 * **One invoice in full**: the window it covers, where it is in its life, what it owes, and the
 * postings that made it.
 *
 * The statement is read **from the card**, which is the only point of view an invoice has: every
 * posting on it has a leg on the card's account, and reading it from anywhere else would state a
 * direction that is nobody's.
 */
internal class GetInvoiceTool(
    private val clock: Clock,
    private val invoiceRepository: IInvoiceRepository,
    private val transactionRepository: ITransactionRepository,
    private val entryRepository: IEntryRepository,
    private val categoryRepository: ICategoryRepository,
    private val installmentRepository: IInstallmentRepository,
) : McpTool {

    override val name: String = McpToolName.GET_INVOICE.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "One invoice in full: the window it covers (when it opened, when it closes, when it " +
            "falls due), where it is in its life, what it owes, and the postings charged to it. " +
            "PERIMETER: the statement is every posting carrying this invoice, read from the card " +
            "— `direction` on each line is which way the money went as the CARD saw it, so a " +
            "purchase is an expense and a payment into the invoice is an income. `spent`, " +
            "`advance_payments` and `adjustment` are the ledger's own breakdown of the same " +
            "money, and `owed` is what is left. All are in the card's own currency. " +
            "For a card's other cycles use list_invoices; for the card's limit, get_card_overview."

    override val inputSchema = schema(
        "id" to number("The invoice's identifier, as list_invoices reports it."),
        "order_by" to choice(
            "How to order the statement. `date` is the day of the posting; `recorded` is the " +
                "order they were entered in.",
            ListingOrder.wireNames,
        ),
        "limit" to number("How many postings to return. Defaults to $DEFAULT_LIMIT, at most $MAX_LIMIT."),
        "offset" to number(
            "How many to skip. The cut is `limit` wide, so the page after this one starts at " +
                "`offset + limit`, and `has_more` says whether there is one.",
        ),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = reading {
        val id = arguments.long("id")
            ?: return@reading refused(
                AgentRefusal(reason = "`id` is required: name the invoice to read."),
            )
        val order = ListingOrder.of(arguments.oneOf("order_by", ListingOrder.wireNames))
        val offset = arguments.count("offset", default = 0, max = MAX_OFFSET)
        val limit = arguments.count("limit", default = DEFAULT_LIMIT, max = MAX_LIMIT, min = 1)

        val invoice = invoiceRepository.getInvoiceById(id)
            ?: return@reading refused(AgentRefusal.notFound("invoice", id))

        val dimensionId = invoice.dimensionId
        val flows = dimensionId?.let { entryRepository.dimensionFlowsByCurrency(it) }
        val owed = dimensionId?.let { entryRepository.dimensionOwedByCurrency(it) } ?: MoneyByCurrency.zero

        val matching = transactionRepository.getAllTransactions()
            .filter { transaction ->
                dimensionId != null && transaction.entries.any { it.dimensionId == dimensionId }
            }
            .inOrder(order)
        val page = matching.pageOf(offset = offset, limit = limit)

        val lookup = TransactionFacadeLookup.of(
            categories = categoryRepository.getAllCategoriesIncludingClosed(),
            installments = installmentRepository.getAllInstallments(),
        )

        // A row the mapper cannot read is dropped rather than failed on — that is its contract — so
        // the page is mapped before it is counted. `returned` states what the answer carries, and
        // taking it from the cut instead would describe a list nobody received.
        val statement = page.items.mapNotNull {
            it.toAgentTransaction(
                // The card is the invoice's only point of view, and every posting on it has a leg
                // there.
                perspective = TransactionPerspective(
                    accountId = invoice.creditCard.accountId,
                    invoiceId = invoice.id,
                ),
                lookup = lookup,
            )
        }

        answer(
            AgentInvoiceDetailAnswer(
                invoice = invoice.toAgentInvoice(owed),
                period = AgentPeriod.range(
                    from = invoice.openingDate,
                    // `to` is inclusive and the window's closing date is not: the cycle's last day
                    // is the one before it, and it is what the period runs through.
                    to = invoice.window.lastAdmittedDate,
                    today = clock.today(),
                ),
                spent = invoice.figure(flows?.expense),
                advancePayments = invoice.figure(flows?.advancePayment),
                adjustment = invoice.figure(flows?.adjustment),
                matching = page.matching,
                returned = statement.size,
                offset = page.offset,
                hasMore = page.hasMore,
                orderedBy = order.wireName,
                statement = statement,
                perimeter = AgentPerimeter(
                    covers = "Every posting carrying this invoice, read from " +
                        "`${invoice.creditCard.name}`, between ${invoice.openingDate} and " +
                        "${invoice.closingDate}.",
                    excludes = listOf(
                        "postings on the same card that landed on a different cycle",
                        "the card's limit and what is left of it — get_card_overview answers those",
                        "anything a page after this one holds — `has_more` says whether there is one",
                    ),
                    seeAlso = listOf(
                        McpToolName.LIST_INVOICES.wireName,
                        McpToolName.GET_CARD_OVERVIEW.wireName,
                        McpToolName.GET_TRANSACTION.wireName,
                    ),
                ),
            ),
        )
    }

    private companion object {
        const val DEFAULT_LIMIT = 100
        const val MAX_LIMIT = 300
        const val MAX_OFFSET = 10_000
    }
}

// ----------------------------------------------------------------------------------
// What the two share
// ----------------------------------------------------------------------------------

/** The invoice states, spelled as the agent spells them. */
private val STATUSES: Map<String, Invoice.Status> =
    Invoice.Status.entries.associateBy { it.name.lowercase() }

private fun Map<Long, MoneyByCurrency>.moneyOf(invoice: Invoice): MoneyByCurrency =
    invoice.dimensionId?.let { this[it] } ?: MoneyByCurrency.zero

private fun Invoice.toAgentInvoice(owed: MoneyByCurrency) = AgentInvoice(
    id = id,
    card = creditCard.name,
    cardId = creditCard.id,
    status = status.name.lowercase(),
    openingDate = openingDate,
    closingDate = closingDate,
    dueDate = dueDate,
    owed = figure(owed),
    paidAt = paidAt,
)

/**
 * One of an invoice's figures, reduced to the single currency it holds.
 *
 * An invoice's dimension only ever lands on the single `LIABILITY` account of its card, so the
 * ledger's per-currency answer has exactly one term — and the reduction happens **here**, beside the
 * facade guarantee that makes it valid, never presumed by the ledger. An invoice with no posting at
 * all has no term, and then the card's own currency denominates the zero.
 */
private fun Invoice.figure(money: MoneyByCurrency?): AgentFigure? {
    money?.singleOrNull()?.let { return AgentFigure.exact(it.value, it.currency) }
    if (money != null && money.isNotEmpty) return null
    return creditCard.currency?.let { AgentFigure.exact(0.0, it) }
}
