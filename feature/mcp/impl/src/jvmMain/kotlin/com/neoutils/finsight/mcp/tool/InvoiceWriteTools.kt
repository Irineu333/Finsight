package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.CreateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.DeleteFutureInvoiceUseCase
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentInvoice
import com.neoutils.finsight.mcp.surface.AgentInvoiceWriteAnswer
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.surface.AgentRemovalAnswer
import kotlinx.serialization.json.JsonObject

/** An invoice as an agent receives it back from a write — no owed figure, because none was read. */
internal fun Invoice.asAgentInvoice() = AgentInvoice(
    id = id,
    card = creditCard.name,
    cardId = creditCard.id,
    status = status.name.lowercase(),
    openingDate = openingDate,
    closingDate = closingDate,
    dueDate = dueDate,
)

// ----------------------------------------------------------------------------------
// create_invoice
// ----------------------------------------------------------------------------------

/**
 * **Declares a cycle of a card that does not exist yet.**
 *
 * What is declared is the *cycle*, never its value: the window and the due day come off the card,
 * and what the invoice is worth arrives later, from postings or from an adjustment. The status is
 * derived and never chosen — a month falling due before the open invoice's is retroactive, from it
 * onwards future — and opening a cycle stays `open_invoice`'s alone.
 */
internal class CreateInvoiceTool(
    private val creditCardRepository: ICreditCardRepository,
    private val createInvoice: CreateInvoiceUseCase,
) : McpTool {

    override val name: String = McpToolName.CREATE_INVOICE.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Bring one of a card's invoices into existence for a month it does not have yet, so a " +
            "purchase can be booked into a cycle other than the open one. " +
            "PERIMETER: it declares the cycle and nothing about its value — the window and the " +
            "due day come off the card, and what the invoice is worth arrives from the postings " +
            "booked into it. Whether it is future or retroactive is derived from the card's open " +
            "invoice and is not chosen here, and this never produces an open one: that is " +
            "open_invoice. A month the card already has an invoice for is refused. " +
            "Recording a purchase does not need this — create_transaction opens the cycle it " +
            "needs on its own."

    override val inputSchema = schema(
        "card_id" to number("The card whose cycle this is, from list_cards."),
        "due_month" to text("The month the invoice falls due in, as `2026-06`."),
        required = listOf("card_id", "due_month"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val card = creditCardRepository.require(arguments.requiredLong("card_id"))
        val dueMonth = arguments.monthOrNull("due_month")
            ?: throw BadArgument(AgentRefusal(reason = "`due_month` is required, as `2026-06`."))

        createInvoice(card.id, dueMonth).reported(
            summary = "invoice of ${card.name} due $dueMonth",
            payload = {
                AgentInvoiceWriteAnswer(
                    invoice = it.asAgentInvoice(),
                    note = "Declared. It is worth nothing until something is booked into it.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.INVOICE, it.id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// delete_invoice
// ----------------------------------------------------------------------------------

/**
 * **Removes an invoice that was declared and never lived.**
 *
 * Only a future or retroactive cycle can go: `Invoice.Status.isDeletable` is the rule, it belongs to
 * the domain, and `DeleteFutureInvoiceUseCase` is what applies it — the same rule the screens read
 * to not offer the action. An open or closed cycle is history, and history is archived, not removed.
 */
internal class DeleteInvoiceTool(
    private val invoiceRepository: IInvoiceRepository,
    private val deleteFutureInvoice: DeleteFutureInvoiceUseCase,
) : McpTool {

    override val name: String = McpToolName.DELETE_INVOICE.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Remove an invoice that was declared but never lived, together with whatever was " +
            "already booked into it. " +
            "PERIMETER: only a future or a retroactive invoice can go. An open, a closed and a " +
            "paid one are refused — an open cycle is where spending is landing right now, and a " +
            "closed or paid one is settled history. Closing and reopening a cycle are " +
            "close_invoice and reopen_invoice; neither removes anything."

    override val inputSchema = schema(
        "id" to number("The invoice to remove, from list_invoices."),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")

        val stored = invoiceRepository.getInvoiceById(id)
            ?: return@writing refusedWith(
                AgentRefusal.notFound("invoice", id),
                summary = "delete invoice $id",
            )

        val summary = "invoice of ${stored.creditCard.name} due ${stored.dueMonth} " +
            "(${stored.status.name.lowercase()})"

        // The use case answers a typed `InvoiceException`, which is a `Throwable` — so the one
        // shape every write reports through holds it as it is, and the refusal that reaches the
        // agent is the invoice's own words.
        deleteFutureInvoice(id).reported(
            summary = summary,
            payload = {
                AgentRemovalAnswer(
                    removed = "invoice",
                    id = id,
                    name = "${stored.creditCard.name} — ${stored.dueMonth}",
                    alsoRemoved = listOf("every posting that had been booked into it"),
                    note = "Removed. The cycle had not started, so nothing settled was lost.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.INVOICE, id) },
        )
    }
}
