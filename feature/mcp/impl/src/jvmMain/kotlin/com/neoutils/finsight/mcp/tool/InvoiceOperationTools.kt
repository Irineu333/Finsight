package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.AdjustInvoiceUseCase
import com.neoutils.finsight.domain.usecase.AdvanceInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CloseInvoiceUseCase
import com.neoutils.finsight.domain.usecase.OpenInvoiceUseCase
import com.neoutils.finsight.domain.usecase.PayInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.ReopenInvoiceUseCase
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentFigure
import com.neoutils.finsight.mcp.surface.AgentInvoice
import com.neoutils.finsight.mcp.surface.AgentInvoiceOperationAnswer
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.surface.toAgentTransaction
import com.neoutils.finsight.ui.model.TransactionPerspective
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * The six operations that move an invoice — through its life cycle, or through a payment.
 *
 * **The trap this family exists around is `pay_invoice`.** Two use cases are named almost alike:
 * `PayInvoiceUseCase` writes `status = PAID` and nothing else, and `PayInvoicePaymentUseCase` posts
 * the payment *and then* calls the first. Reaching for the shorter name marks the bill paid with the
 * money still in the account — the balance then lies, the card's `LIABILITY` legs stay standing, and
 * **nothing fails**. So this tool holds the second one, and holds no other way of settling a bill.
 *
 * The rest follow the same line the whole surface is built on: closing, opening, reopening and
 * adjusting are decisions with owners, and what arrives here is only the identity to apply them to.
 */

/** How an invoice is described to the person who will read the activity log. */
private fun Invoice.asLogLine(): String =
    "invoice of ${creditCard.name} due $dueMonth (${status.name.lowercase()})"

/**
 * The invoice as it stands **after** the operation, with what it now owes.
 *
 * Read back rather than carried through: every operation here changes the status, and three of them
 * change the balance, so answering with the copy the call started from would describe the invoice as
 * it was before the act the answer is about.
 */
private suspend fun IInvoiceRepository.answerFor(
    invoiceId: Long,
    fallback: Invoice,
    calculateInvoice: CalculateInvoiceUseCase,
): AgentInvoice {
    val invoice = getInvoiceById(invoiceId) ?: fallback
    val currency = invoice.creditCard.currency ?: return invoice.asAgentInvoice()
    return invoice.asAgentInvoice().copy(
        owed = AgentFigure.exact(calculateInvoice(invoice), currency),
        paidAt = invoice.paidAt,
    )
}

// ----------------------------------------------------------------------------------
// pay_invoice
// ----------------------------------------------------------------------------------

/**
 * **Settles a closed invoice in full: the money leaves the account and the invoice becomes paid, in
 * one act.**
 *
 * The two halves are one operation and not two, which is the whole reason this tool exists rather
 * than a `mark_invoice_paid`: the status is derived from a payment having been written, and a status
 * written on its own is a statement the ledger does not support.
 *
 * A paying account in another currency states what leaves it through `paid_amount`; the invoice's
 * own side stays exactly what it owes, because that is a fact and not a choice. **No rate is a
 * parameter**: it is the quotient of the two ends, derived afterwards by the domain.
 */
internal class PayInvoiceTool(
    private val clock: Clock,
    private val invoiceRepository: IInvoiceRepository,
    private val accountRepository: IAccountRepository,
    private val calculateInvoice: CalculateInvoiceUseCase,
    private val payInvoicePayment: PayInvoicePaymentUseCase,
) : McpTool {

    override val name: String = McpToolName.PAY_INVOICE.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Pay a card bill in full: it posts the payment out of the account and marks the invoice " +
            "paid, as one act. " +
            "PERIMETER: only a closed invoice that still owes something can be paid, and it is " +
            "paid in full — a cycle without a final figure has no whole to settle. Paying part " +
            "of one that is still taking spending is advance_invoice_payment; a retroactive " +
            "invoice is settled that way, paid down until it owes nothing and then marked paid " +
            "by close_invoice, which settles nothing unless the invoice already owes zero. " +
            "When the paying account is denominated differently from the card, give paid_amount " +
            "— what leaves the account. What the invoice owes is not negotiable and stays in the " +
            "card's currency, and the rate is derived from the two ends rather than given."

    override val inputSchema = schema(
        "id" to number("The invoice to pay, from list_invoices."),
        "account_id" to number("The account the money leaves, from list_accounts."),
        "date" to text(
            "The day it was paid, as `2026-03-14`. Defaults to today. It has to fall between " +
                "the invoice's closing date and its due date, and never in the future.",
        ),
        "paid_amount" to amount(
            "What leaves the account, in the account's own currency — only when that currency " +
                "differs from the card's. Leave it out otherwise.",
        ),
        required = listOf("id", "account_id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")
        val account = accountRepository.require(arguments.requiredLong("account_id"))
        val date = arguments.date("date") ?: clock.today()
        val paidAmount = arguments.money("paid_amount")

        val stored = invoiceRepository.getInvoiceById(id)
            ?: return@writing refusedWith(
                AgentRefusal.notFound("invoice", id),
                summary = "pay invoice $id",
            )

        val summary = "${stored.asLogLine()} paid from ${account.name}"

        // `PayInvoicePaymentUseCase`, never `PayInvoiceUseCase`: the second one only writes the
        // status, so the bill would read as settled with the money still in the account and
        // nothing anywhere would disagree.
        payInvoicePayment(
            invoiceId = id,
            date = date,
            accountId = account.id,
            paidAmount = paidAmount,
        ).reported(
            summary = summary,
            payload = {
                AgentInvoiceOperationAnswer(
                    invoice = invoiceRepository.answerFor(id, it, calculateInvoice),
                    note = "Paid. The payment was posted out of ${account.name}, so the account's " +
                        "balance fell by it and the card's debt was settled.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.INVOICE, id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// advance_invoice_payment
// ----------------------------------------------------------------------------------

/**
 * **Pays part of an open cycle before it closes.**
 *
 * `amount` is in the **card's** currency and always has been, which is what makes the ceiling
 * correct: it is compared against what the invoice owes, and comparing it against what leaves the
 * account would be comparing two currencies. The account's side carries no ceiling for that reason.
 */
internal class AdvanceInvoicePaymentTool(
    private val clock: Clock,
    private val invoiceRepository: IInvoiceRepository,
    private val accountRepository: IAccountRepository,
    private val calculateInvoice: CalculateInvoiceUseCase,
    private val advanceInvoicePayment: AdvanceInvoicePaymentUseCase,
) : McpTool {

    override val name: String = McpToolName.ADVANCE_INVOICE_PAYMENT.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Pay part of a card bill ahead of time, while its cycle is still running. " +
            "PERIMETER: it posts a partial payment and leaves the cycle open — the invoice is " +
            "not marked paid and not closed. Settling a closed bill in full is pay_invoice. " +
            "The date has to fall inside the invoice's own window, and amount cannot exceed what " +
            "the invoice owes. " +
            "amount is in the card's currency; when the paying account is denominated " +
            "differently, paid_amount says what leaves the account, and the rate is derived from " +
            "the two ends rather than given."

    override val inputSchema = schema(
        "id" to number("The invoice being paid down, from list_invoices."),
        "amount" to amount("How much of the invoice is being settled, in the card's currency."),
        "account_id" to number("The account the money leaves, from list_accounts."),
        "date" to text(
            "The day it was paid, as `2026-03-14`. Defaults to today, falls inside the " +
                "invoice's window, and is never in the future.",
        ),
        "paid_amount" to amount(
            "What leaves the account, in the account's own currency — only when that currency " +
                "differs from the card's.",
        ),
        required = listOf("id", "amount", "account_id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")
        val amount = arguments.requiredMoney("amount")
        val account = accountRepository.require(arguments.requiredLong("account_id"))
        val date = arguments.date("date") ?: clock.today()
        val paidAmount = arguments.money("paid_amount")

        val stored = invoiceRepository.getInvoiceById(id)
            ?: return@writing refusedWith(
                AgentRefusal.notFound("invoice", id),
                summary = "pay $amount towards invoice $id",
            )

        val summary = "$amount towards ${stored.asLogLine()} from ${account.name}"

        advanceInvoicePayment(
            invoiceId = id,
            amount = amount,
            date = date,
            accountId = account.id,
            paidAmount = paidAmount,
        ).reported(
            summary = summary,
            payload = { transaction ->
                AgentInvoiceOperationAnswer(
                    invoice = invoiceRepository.answerFor(id, stored, calculateInvoice),
                    // Read from the paying account's side, because that is the end the caller
                    // named: the direction is the money leaving it, and the figure is in its own
                    // currency even where the card's differs.
                    transaction = transaction.toAgentTransaction(
                        perspective = TransactionPerspective(account.id),
                    ),
                    note = "Paid in part. The cycle stays open and the invoice is not settled; " +
                        "`owed` is what is left on it.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.TRANSACTION, it.id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// close_invoice
// ----------------------------------------------------------------------------------

/**
 * **Ends a cycle: the invoice stops taking spending, and the card's next one opens.**
 *
 * Closing settles nothing. An invoice that owes zero is marked paid by closing — there is nothing
 * to write — and one with a balance keeps its legs standing until `pay_invoice` writes the payment.
 */
internal class CloseInvoiceTool(
    private val clock: Clock,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoice: CalculateInvoiceUseCase,
    private val closeInvoice: CloseInvoiceUseCase,
) : McpTool {

    override val name: String = McpToolName.CLOSE_INVOICE.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Close a card's cycle: the invoice stops taking new spending and the card's next cycle " +
            "opens in its place. " +
            "PERIMETER: closing settles nothing. An invoice that owes zero is marked paid by " +
            "closing, because there is nothing to write; one with a balance stays owed until " +
            "pay_invoice posts the payment. The date has to fall inside the invoice's closing " +
            "month and on or after its closing date — a cycle is not closed before the day it " +
            "closes on. Undoing this is reopen_invoice."

    override val inputSchema = schema(
        "id" to number("The invoice to close, from list_invoices."),
        "date" to text(
            "The day it closed, as `2026-03-14`. Defaults to today, and has to fall in the " +
                "invoice's closing month, on or after its closing date.",
        ),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")
        val date = arguments.date("date") ?: clock.today()

        val stored = invoiceRepository.getInvoiceById(id)
            ?: return@writing refusedWith(
                AgentRefusal.notFound("invoice", id),
                summary = "close invoice $id",
            )

        closeInvoice(id, date).reported(
            summary = stored.asLogLine(),
            payload = {
                AgentInvoiceOperationAnswer(
                    invoice = invoiceRepository.answerFor(id, it, calculateInvoice),
                    note = "Closed. Its `status` says whether closing also settled it, which " +
                        "happens only when it owed nothing.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.INVOICE, id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// open_invoice
// ----------------------------------------------------------------------------------

/**
 * **Puts a card's cycle on the air: the invoice opening in a month becomes the one spending lands
 * in.**
 *
 * A future invoice already declared for that month is promoted rather than duplicated. A card is
 * only ever spending into one invoice, so an opening that would straddle a cycle it already has is
 * refused by the domain.
 */
internal class OpenInvoiceTool(
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoice: CalculateInvoiceUseCase,
    private val openInvoice: OpenInvoiceUseCase,
) : McpTool {

    override val name: String = McpToolName.OPEN_INVOICE.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Put a card's cycle on the air, so new spending lands in it. " +
            "PERIMETER: a card has exactly one open cycle, so an opening that would overlap one " +
            "it already has is refused. A future invoice already declared for that month is " +
            "promoted rather than duplicated. Declaring a cycle without opening it is " +
            "create_invoice, and recording a purchase needs neither — create_transaction opens " +
            "the cycle it needs on its own."

    override val inputSchema = schema(
        "card_id" to number("The card whose cycle this is, from list_cards."),
        "opening_month" to text(
            "The month the cycle starts taking spending, as `2026-03`. It closes the month " +
                "after, and falls due on the day the card says.",
        ),
        required = listOf("card_id", "opening_month"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val card = creditCardRepository.require(arguments.requiredLong("card_id"))
        val openingMonth = arguments.monthOrNull("opening_month")
            ?: throw BadArgument(AgentRefusal(reason = "`opening_month` is required, as `2026-03`."))

        openInvoice(card.id, openingMonth).reported(
            summary = "invoice of ${card.name} opening $openingMonth",
            payload = {
                AgentInvoiceOperationAnswer(
                    invoice = invoiceRepository.answerFor(it.id, it, calculateInvoice),
                    note = "Open. New spending on ${card.name} lands here until it is closed.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.INVOICE, it.id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// reopen_invoice
// ----------------------------------------------------------------------------------

/**
 * **Puts a closed invoice back on the air, demoting the successor that opened in its place.**
 *
 * Only the latest closed invoice reopens, or the card would end up with two open cycles. That rule
 * is the domain's, and it is the same one the screens read to not offer the action.
 */
internal class ReopenInvoiceTool(
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoice: CalculateInvoiceUseCase,
    private val reopenInvoice: ReopenInvoiceUseCase,
) : McpTool {

    override val name: String = McpToolName.REOPEN_INVOICE.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Undo the closing of a cycle: the invoice takes spending again, and the successor that " +
            "opened in its place goes back to being a future one. " +
            "PERIMETER: only the most recently closed invoice of a card can come back, because a " +
            "card is only ever spending into one. An invoice that is already open, and one that " +
            "was paid, are refused — undoing a payment means removing the posting that made it " +
            "(delete_transaction), not reopening the cycle."

    override val inputSchema = schema(
        "id" to number("The invoice to reopen, from list_invoices."),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")

        val stored = invoiceRepository.getInvoiceById(id)
            ?: return@writing refusedWith(
                AgentRefusal.notFound("invoice", id),
                summary = "reopen invoice $id",
            )

        reopenInvoice(id).reported(
            summary = stored.asLogLine(),
            payload = {
                AgentInvoiceOperationAnswer(
                    invoice = invoiceRepository.answerFor(id, it, calculateInvoice),
                    note = "Open again. The cycle that had opened after it went back to future.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.INVOICE, id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// adjust_invoice
// ----------------------------------------------------------------------------------

/**
 * **Corrects what an invoice owes, by posting the difference.**
 *
 * It writes an adjustment, and does not edit a field: the invoice's balance is `Σ entries` and there
 * is no number anywhere to overwrite. Re-adjusting the same date rewrites that same posting from its
 * own ledger leg, so corrections never accumulate onto a stale value.
 */
internal class AdjustInvoiceTool(
    private val clock: Clock,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoice: CalculateInvoiceUseCase,
    private val adjustInvoice: AdjustInvoiceUseCase,
) : McpTool {

    override val name: String = McpToolName.ADJUST_INVOICE.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Correct what an invoice owes to a stated figure, by posting the difference. " +
            "PERIMETER: it posts an adjustment; it does not edit a number. What an invoice owes " +
            "is the sum of its entries, so the correction is itself an entry — visible in the " +
            "statement and removable like any other. Adjusting to the figure it already owes is " +
            "refused: there is nothing to record. Correcting one purchase is update_transaction, " +
            "and correcting an account's balance is adjust_balance."

    override val inputSchema = schema(
        "id" to number("The invoice to correct, from list_invoices."),
        "target" to amount("What it should owe after the correction, in the card's currency."),
        "date" to text("The day the correction belongs to, as `2026-03-14`. Defaults to today."),
        required = listOf("id", "target"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")
        val target = arguments.requiredMoney("target")
        val date = arguments.date("date") ?: clock.today()

        val stored = invoiceRepository.getInvoiceById(id)
            ?: return@writing refusedWith(
                AgentRefusal.notFound("invoice", id),
                summary = "adjust invoice $id to $target",
            )

        adjustInvoice(id, target, date).reported(
            summary = "${stored.asLogLine()} to $target",
            payload = {
                AgentInvoiceOperationAnswer(
                    invoice = invoiceRepository.answerFor(id, stored, calculateInvoice),
                    note = "Corrected. The difference was posted as an adjustment dated $date.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.INVOICE, id) },
        )
    }
}
