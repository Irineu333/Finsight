package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.Limit
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentCard
import com.neoutils.finsight.mcp.surface.AgentCardOverview
import com.neoutils.finsight.mcp.surface.AgentCardOverviewAnswer
import com.neoutils.finsight.mcp.surface.AgentFigure
import com.neoutils.finsight.mcp.surface.AgentInvoice
import com.neoutils.finsight.mcp.surface.AgentPerimeter
import com.neoutils.finsight.mcp.surface.AgentRefusal
import kotlinx.serialization.json.JsonObject

/**
 * **Each card's limit and the invoice standing open on it right now.**
 *
 * Its recorte, against the invoice listings of the catalogue family: this is the **card's** answer —
 * one line per card, its limit, what its unpaid invoices owe together, and the single invoice that
 * is open at this moment. It never enumerates a card's invoices, and it never reaches a closed or a
 * future one. A question about a specific invoice, or about a card's history of them, is
 * `get_invoice` and `list_invoices`.
 *
 * Every figure is in the card's **own** currency — the one its ledger account states and never
 * changes — and nothing here consolidates: adding the limits of two cards in different currencies is
 * conversion, and this is not the place for it.
 */
internal class GetCardOverviewTool(
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateAvailableLimit: CalculateAvailableLimitUseCase,
    private val calculateInvoice: CalculateInvoiceUseCase,
) : McpTool {

    override val name: String = McpToolName.GET_CARD_OVERVIEW.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "Each credit card with its limit, how much of it is taken by unpaid invoices, how much " +
            "is left, and the invoice open on it right now. " +
            "PERIMETER: one line per card, and only the invoice that is open at this moment. " +
            "Closed, future and paid invoices are not here — for those, and for a card's " +
            "invoices over time, use list_invoices; for one invoice in full, get_invoice. " +
            "`used`, `available` and `limit` are three separate readings, not two plus a " +
            "subtraction: what is available is what the app computes, not `limit - used`. " +
            "Figures are in each card's own currency and are never added across cards."

    override val inputSchema = schema(
        "card_id" to number("Scopes the answer to one card. Omit for every card."),
        "include_archived" to yesOrNo(
            "Include cards the user has archived. Defaults to false.",
        ),
    )

    override suspend fun call(arguments: JsonObject?) = reading {
        val cardId = arguments.long("card_id")
        val includeArchived = arguments.flag("include_archived", default = false)

        val cards = when (cardId) {
            null -> if (includeArchived) {
                creditCardRepository.getAllCreditCardsIncludingClosed()
            } else {
                creditCardRepository.getAllCreditCards()
            }

            else -> listOf(
                creditCardRepository.getCreditCardById(cardId)
                    ?: return@reading refused(AgentRefusal.notFound("credit card", cardId)),
            )
        }

        // One read for every card, not one per card: the plural form is the canonical one precisely
        // because a list is what asks this question (design D7).
        val limits = calculateAvailableLimit(cards.map { it.id })
        val openInvoices = cards.mapNotNull { invoiceRepository.getOpenInvoice(it.id) }
        val owed = calculateInvoice(openInvoices)

        answer(
            AgentCardOverviewAnswer(
                cards = cards.map { card ->
                    val limit = limits[card.id] ?: Limit.NONE
                    val open = openInvoices.firstOrNull { it.creditCard.id == card.id }
                    AgentCardOverview(
                        card = card.toAgentCard(limit),
                        openInvoice = open?.toAgentInvoice(card, owed[open.id] ?: 0.0),
                        unpaidTotal = card.figure(limit.totalUnpaidAmount),
                        // A card with no limit has no fraction of one in use: `null` says there is
                        // no answer, where `0` would claim the card is untouched.
                        limitUsage = limit.usage.takeIf { card.limit > 0.0 },
                    )
                },
                perimeter = AgentPerimeter(
                    covers = "Every card the user holds, with the invoice open on it now and what " +
                        "all of its unpaid invoices owe together.",
                    excludes = listOf(
                        "closed, future and already paid invoices",
                        "spending on a card that has not yet landed on an invoice",
                        "any total across cards — figures stay in each card's own currency",
                    ) + if (includeArchived) emptyList() else listOf("archived cards"),
                    seeAlso = listOf(
                        McpToolName.LIST_INVOICES.wireName,
                        McpToolName.GET_INVOICE.wireName,
                        McpToolName.GET_NET_WORTH.wireName,
                    ),
                ),
            )
        )
    }

    private fun CreditCard.toAgentCard(limit: Limit) = AgentCard(
        id = id,
        name = name,
        currency = currency,
        closingDay = closingDay,
        dueDay = dueDay,
        limit = figure(this.limit),
        used = figure(limit.totalUnpaidAmount),
        available = figure(limit.available),
        isArchived = isArchived,
    )

    private fun Invoice.toAgentInvoice(card: CreditCard, owed: Double) = AgentInvoice(
        id = id,
        card = card.name,
        cardId = card.id,
        status = status.name.lowercase(),
        openingDate = openingDate,
        closingDate = closingDate,
        dueDate = dueDate,
        owed = card.figure(owed),
        paidAt = paidAt,
    )

    /**
     * A card's figure, in the card's own currency.
     *
     * `null` when the card carries no currency — an instance that never came from a hydrated read.
     * A default here would denominate somebody's money by guessing, which is the one thing the money
     * types in this app exist to make impossible.
     */
    private fun CreditCard.figure(amount: Double): AgentFigure? =
        currency?.let { AgentFigure.exact(amount, it) }
}
