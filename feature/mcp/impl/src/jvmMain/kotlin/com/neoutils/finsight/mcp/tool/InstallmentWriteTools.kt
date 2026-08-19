package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCase
import com.neoutils.finsight.domain.usecase.DeleteInstallmentUseCase
import com.neoutils.finsight.domain.usecase.UpdateInstallmentUseCase
import com.neoutils.finsight.extension.isAccept
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentFigure
import com.neoutils.finsight.mcp.surface.AgentInstallment
import com.neoutils.finsight.mcp.surface.AgentInstallmentWriteAnswer
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.surface.AgentRemovalAnswer
import com.neoutils.finsight.mcp.surface.AgentTransactionWriteAnswer
import com.neoutils.finsight.mcp.surface.toAgentTransaction
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

// ----------------------------------------------------------------------------------
// create_installment
// ----------------------------------------------------------------------------------

/**
 * **Splits a card purchase into instalments**, one posting per invoice they land on.
 *
 * How the shares are distributed — which invoice each falls on, and how the rounding is carried — is
 * `AddInstallmentUseCase`'s, and the N postings are written as one unit: writing seven of twelve and
 * failing would leave a plan describing money that was never recorded.
 */
internal class CreateInstallmentTool(
    private val clock: Clock,
    private val creditCardRepository: ICreditCardRepository,
    private val categoryRepository: ICategoryRepository,
    private val installmentRepository: IInstallmentRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val addInstallment: AddInstallmentUseCase,
) : McpTool {

    override val name: String = McpToolName.CREATE_INSTALLMENT.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Split a card purchase into instalments: one posting per invoice they land on, written " +
            "all together or not at all. " +
            "The amount is the **total** of the purchase, not the value of one share — the app " +
            "divides it and carries the rounding. " +
            "PERIMETER: instalments exist on a credit card only, and only for expenses. " +
            "create_transaction with `installments` does exactly the same thing; this tool is " +
            "the one to reach for when the split is the point. Correcting how many shares a plan " +
            "has, or the total it declares, is update_installment and moves no money."

    override val inputSchema = schema(
        "card_id" to number("The card the purchase was charged to, from list_cards."),
        "amount" to amount("The total of the purchase, in the card's currency — 1200.00, not 120000."),
        "count" to number("How many instalments to split it into. At least 2."),
        "date" to text("The day of the purchase, as `2026-03-14`. Defaults to today."),
        "title" to text("What was bought. Required unless a category is given."),
        "category_id" to number(
            "The category to classify it under, from list_categories. An expense category — a " +
                "split is always an expense, and an income one is refused.",
        ),
        "invoice_month" to text(
            "Which invoice the first instalment lands on, as `2026-04`. Defaults to the card's " +
                "open invoice; the rest follow it, one per month.",
        ),
        required = listOf("card_id", "amount", "count"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val card = creditCardRepository.require(arguments.requiredLong("card_id"))
        val amount = arguments.requiredMoney("amount")
        val count = arguments.requiredLong("count").toInt()
        val date = arguments.date("date") ?: clock.today()
        val title = arguments.string("title")
        val category = arguments.long("category_id")?.let { categoryRepository.require(it) }

        val summary = "${title ?: category?.name ?: "untitled"}, $amount on ${card.name} in $count instalments"

        // `TransactionForm.from` normalises by dropping what does not fit, and that is right for
        // the sheet: its selectors never offer an income category where the direction is fixed to
        // expense, so the drop takes nothing the user chose. The argument here was declared instead
        // of offered, so the same drop writes every share with no classification at all under an
        // answer that says they were recorded.
        if (category != null && !category.type.isAccept(TransactionType.EXPENSE)) {
            return@writing refusedWith(
                AgentRefusal(
                    reason = "`category_id` names \"${category.name}\", an " +
                        "${category.type.name.lowercase()} category, and a split is always an " +
                        "expense: a category classifies one direction only. Give an expense " +
                        "category, or leave `category_id` out.",
                ),
                summary = summary,
            )
        }

        val form = TransactionForm.from(
            type = TransactionType.EXPENSE,
            amount = amount.asFormAmount(),
            title = title,
            date = date.asFormDate(),
            category = category,
            target = TransactionTarget.CREDIT_CARD,
            creditCard = card,
            invoiceDueMonth = arguments.monthOrNull("invoice_month")
                ?: invoiceRepository.getOpenInvoice(card.id)?.dueMonth,
            account = null,
            installments = count,
        )

        addInstallment(form, count).fold(
            ifLeft = { refusedBy(it, summary) },
            ifRight = { written ->
                val planId = written.firstNotNullOfOrNull { it.installmentId }
                val plan = planId?.let { installmentRepository.getInstallmentById(it) }
                val lookup = TransactionFacadeLookup.of(
                    categories = categoryRepository.getAllCategoriesIncludingClosed(),
                    installments = listOfNotNull(plan),
                )

                applied(
                    payload = AgentTransactionWriteAnswer(
                        transaction = written.first().toAgentTransaction(lookup = lookup)!!,
                        transactions = written.mapNotNull { it.toAgentTransaction(lookup = lookup) },
                        installment = plan?.let {
                            AgentInstallment(
                                id = it.id,
                                title = title,
                                card = card.name,
                                cardId = card.id,
                                count = it.count,
                                total = card.currency?.let { currency ->
                                    AgentFigure.exact(it.totalAmount, currency)
                                },
                            )
                        },
                        note = "Recorded as ${written.size} instalments, one per invoice they land on.",
                    ),
                    summary = summary,
                    reference = reference(
                        AgentActivity.Reference.Kind.INSTALLMENT,
                        planId ?: written.first().id,
                    ),
                )
            },
        )
    }
}

// ----------------------------------------------------------------------------------
// update_installment
// ----------------------------------------------------------------------------------

/**
 * **Corrects what a plan says about itself**: how many shares it has and the total the user
 * declared.
 *
 * Neither is derived, and neither is money. Per-share rounding means the shares need not add up to
 * the declared total — a hundred in three is three times 33.33 — so the total is the user's word,
 * and writing it is an edit rather than a recalculation. The postings in the ledger are untouched.
 */
internal class UpdateInstallmentTool(
    private val installmentRepository: IInstallmentRepository,
    private val updateInstallment: UpdateInstallmentUseCase,
) : McpTool {

    override val name: String = McpToolName.UPDATE_INSTALLMENT.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Correct what an instalment plan says about itself: how many shares it has, and the " +
            "total the user declared for it. " +
            "PERIMETER: this moves no money. The postings already in the ledger are left exactly " +
            "as they are — it changes the plan's own description of itself, which is why the " +
            "shares need not add up to the total (per-share rounding). To change what a single " +
            "share cost, edit that posting with update_transaction; to remove the plan and its " +
            "postings, use delete_installment."

    override val inputSchema = schema(
        "id" to number("The plan to correct, from list_installments."),
        "count" to number("How many shares it has. At least 1."),
        "total_amount" to amount("The total the user declared, in the card's currency — 1200.00, not 120000."),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")

        val stored = installmentRepository.getInstallmentById(id)
            ?: return@writing refusedWith(
                AgentRefusal.notFound("installment", id),
                summary = "edit installment $id",
            )

        val count = arguments.long("count")?.toInt() ?: stored.count
        val total = arguments.money("total_amount") ?: stored.totalAmount

        updateInstallment(id, count, total).reported(
            summary = "installment $id, ${stored.count} shares of ${stored.totalAmount}",
            payload = {
                AgentInstallmentWriteAnswer(
                    installment = AgentInstallment(id = it.id, count = it.count),
                    note = "Corrected. The postings in the ledger were not touched.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.INSTALLMENT, it.id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// delete_installment
// ----------------------------------------------------------------------------------

/** **Removes a plan and every posting that belongs to it, as one unit.** */
internal class DeleteInstallmentTool(
    private val installmentRepository: IInstallmentRepository,
    private val deleteInstallment: DeleteInstallmentUseCase,
) : McpTool {

    override val name: String = McpToolName.DELETE_INSTALLMENT.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Remove an instalment plan for good, together with **every** posting that belongs to it " +
            "— the ones already on closed invoices included. " +
            "PERIMETER: one decision, one unit of work: either all the postings go or none does. " +
            "To remove a single share, use delete_transaction on that posting; to correct what " +
            "the plan says about itself without touching the ledger, use update_installment."

    override val inputSchema = schema(
        "id" to number("The plan to remove, from list_installments."),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")

        val stored = installmentRepository.getInstallmentById(id)
            ?: return@writing refusedWith(
                AgentRefusal.notFound("installment", id),
                summary = "delete installment $id",
            )

        deleteInstallment(id).reported(
            summary = "installment $id, ${stored.count} shares of ${stored.totalAmount}",
            payload = {
                AgentRemovalAnswer(
                    removed = "installment",
                    id = id,
                    alsoRemoved = listOf("every posting that belonged to the plan"),
                    note = "Removed, with all ${stored.count} of its postings.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.INSTALLMENT, id) },
        )
    }
}
