package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.displayTitleOf
import com.neoutils.finsight.extension.liabilityLeg
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentFigure
import com.neoutils.finsight.mcp.surface.AgentInstallment
import com.neoutils.finsight.mcp.surface.AgentInstallmentListAnswer
import com.neoutils.finsight.mcp.surface.AgentPerimeter
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import kotlinx.serialization.json.JsonObject

/**
 * **The instalment plans**, and how far each one has got.
 *
 * A plan is not a posting: it is the N postings the ledger already holds, one per invoice, plus the
 * arrangement they belong to. So everything about its progress is read off those postings and the
 * invoices they landed on — an instalment counts as paid when the invoice carrying it was paid,
 * which is the only fact in the app that says so.
 *
 * Every figure is the **card's**, because a card is the one account an instalment names. A plan
 * whose postings resolve to no card leg is left out rather than denominated by guessing.
 */
internal class ListInstallmentsTool(
    private val installmentRepository: IInstallmentRepository,
    private val transactionRepository: ITransactionRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
) : McpTool {

    override val name: String = McpToolName.LIST_INSTALLMENTS.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "The instalment plans, with how many instalments each has, how many are already paid, " +
            "how many are left, what the whole plan costs and what one instalment costs. " +
            "PERIMETER: an instalment counts as paid when the invoice carrying it was paid — not " +
            "when its date passed. Every figure is in the card's own currency, and no total is " +
            "taken across plans. A plan whose postings were all removed is not here: the plan is " +
            "its postings. " +
            "For the postings themselves, filter list_transactions by the card."

    override val inputSchema = schema(
        "card_id" to number("Only the plans charged to this card. Omit for every plan."),
        "status" to choice("Which plans to return.", STATUSES),
    )

    override suspend fun call(arguments: JsonObject?) = reading {
        val cardId = arguments.long("card_id")
        val status = arguments.oneOf("status", STATUSES) ?: ALL

        val cardsByAccount = creditCardRepository.getAllCreditCardsIncludingClosed()
            .associateBy { it.accountId }
        val invoicesByDimension = invoiceRepository.getAllInvoices()
            .mapNotNull { invoice -> invoice.dimensionId?.let { it to invoice } }
            .toMap()
        val lookup = TransactionFacadeLookup.of(
            categories = categoryRepository.getAllCategoriesIncludingClosed(),
        )

        val byPlan = transactionRepository.getAllTransactions()
            .filter { it.installmentId != null }
            .groupBy { checkNotNull(it.installmentId) }

        val installments = installmentRepository.getAllInstallments()
            .mapNotNull { plan ->
                plan.toAgentInstallment(
                    transactions = byPlan[plan.id].orEmpty(),
                    cardsByAccount = cardsByAccount,
                    invoicesByDimension = invoicesByDimension,
                    lookup = lookup,
                )
            }
            .filter { cardId == null || it.cardId == cardId }
            .filter {
                when (status) {
                    ACTIVE -> it.remaining != 0
                    COMPLETED -> it.remaining == 0
                    else -> true
                }
            }
            // Newest plan first, with the identity breaking the tie: two plans started on the same
            // day are ordered the same way in every call.
            .sortedByDescending { it.id }

        answer(
            AgentInstallmentListAnswer(
                installments = installments,
                perimeter = AgentPerimeter(
                    covers = "Every instalment plan the filter matches, with its progress read " +
                        "off the invoices its instalments landed on.",
                    excludes = listOf(
                        "plans with no posting left — a plan is the postings it created",
                        "plans whose postings carry no card leg, which nothing denominates",
                        "any total across plans: each figure stays in its card's own currency",
                    ),
                    seeAlso = listOf(
                        McpToolName.LIST_TRANSACTIONS.wireName,
                        McpToolName.LIST_INVOICES.wireName,
                    ),
                ),
            ),
        )
    }

    /**
     * One plan as the agent receives it, or `null` when it has nothing to be read from.
     *
     * The currency is the card leg's, and its absence is what makes the plan unreportable: an
     * amount with no currency is the one payload this surface never produces.
     */
    private fun Installment.toAgentInstallment(
        transactions: List<Transaction>,
        cardsByAccount: Map<Long, CreditCard>,
        invoicesByDimension: Map<Long, Invoice>,
        lookup: TransactionFacadeLookup,
    ): AgentInstallment? {
        val ordered = transactions.sortedBy { it.installmentNumber ?: Int.MAX_VALUE }
        val first = ordered.firstOrNull() ?: return null
        val cardLeg = ordered.firstNotNullOfOrNull { it.entries.liabilityLeg() } ?: return null
        val card = cardsByAccount[cardLeg.account.id]

        val paid = ordered.count { transaction ->
            transaction.liabilityDimensionId
                ?.let { invoicesByDimension[it] }
                ?.status
                ?.isPaid == true
        }

        return AgentInstallment(
            id = id,
            title = displayTitleOf(first.title, lookup.categoryOf(first)),
            card = card?.name ?: cardLeg.account.name,
            cardId = card?.id,
            count = count,
            paid = paid,
            remaining = count - paid,
            total = AgentFigure.exact(totalAmount, cardLeg.currency),
            installmentAmount = AgentFigure.exact(totalAmount / count, cardLeg.currency),
        )
    }

    private companion object {

        const val ACTIVE = "active"
        const val COMPLETED = "completed"
        const val ALL = "all"

        val STATUSES = listOf(ACTIVE, COMPLETED, ALL)
    }
}
