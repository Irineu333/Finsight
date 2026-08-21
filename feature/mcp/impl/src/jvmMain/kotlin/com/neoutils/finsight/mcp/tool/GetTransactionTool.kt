package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.matches
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.impliedRate
import com.neoutils.finsight.extension.deriveTransactionLabel
import com.neoutils.finsight.extension.deriveTransactionType
import com.neoutils.finsight.extension.displayTitleOf
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentAppliedRate
import com.neoutils.finsight.mcp.surface.AgentFigure
import com.neoutils.finsight.mcp.surface.AgentLeg
import com.neoutils.finsight.mcp.surface.AgentPerimeter
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.surface.AgentTransactionDetailAnswer
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import kotlinx.serialization.json.JsonObject
import kotlin.math.abs

/**
 * **One posting, with every leg that holds money** — which is what a listing cannot show.
 *
 * A line of a list is a single leg, because a list is a list of lines. An operation is not: a
 * transfer moved money out of one account and into another, and a card payment took it out of an
 * account and off a card. Both ends are the ledger's own exact amounts, and showing one of them as
 * "the amount" is how a cross-currency payment comes to be reported in a currency the user keeps no
 * account in.
 *
 * When the two ends disagree on currency the answer carries the rate the operation **actually got**
 * — the quotient of its own two legs, exactly as the form that wrote it derived it. It is never a
 * rate from the archive: what an operation was done at is a fact about the operation, and a stored
 * rate is a fact about a day.
 */
internal class GetTransactionTool(
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val installmentRepository: IInstallmentRepository,
    private val invoiceRepository: IInvoiceRepository,
) : McpTool {

    override val name: String = McpToolName.GET_TRANSACTION.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "One posting in full: its nature, its category, and EVERY leg of it that holds money — " +
            "an account or a card — with the amount that landed on each. " +
            "PERIMETER: this is the operation, not a line of a list. A transfer and a card " +
            "payment have two monetary legs and both are here, each exact in its own account's " +
            "currency, signed as the ledger recorded it (negative left the account or was " +
            "charged to the card). When the two legs are in different currencies, " +
            "`applied_rate` is the rate THIS operation got, taken from its own two ends — not a " +
            "rate from the exchange-rate archive. " +
            "For the postings of a month, and the totals that go with them, use list_transactions."

    override val inputSchema = schema(
        "id" to number("The posting's identifier, as list_transactions reports it."),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = reading {
        val id = arguments.long("id")
            ?: return@reading refused(
                AgentRefusal(reason = "`id` is required: name the posting to read."),
            )

        val transaction = transactionRepository.getTransactionById(id)
            ?: return@reading refused(AgentRefusal.notFound("transaction", id))

        val lookup = TransactionFacadeLookup.of(
            categories = categoryRepository.getAllCategoriesIncludingClosed(),
            installments = installmentRepository.getAllInstallments(),
        )
        val category = lookup.categoryOf(transaction)
        val invoicesByDimension = invoiceRepository.getAllInvoices()
            .mapNotNull { invoice -> invoice.dimensionId?.let { it to invoice } }
            .toMap()

        answer(
            AgentTransactionDetailAnswer(
                id = transaction.id,
                nature = transaction.entries.deriveTransactionLabel().name.lowercase(),
                title = displayTitleOf(transaction.title, category),
                date = transaction.date,
                legs = transaction.monetaryEntries.map { it.toAgentLeg(transaction, invoicesByDimension) },
                category = category?.name,
                categoryId = category?.id,
                // Decided by the single owner of what a value of the analytic axis contains: a
                // transfer has no nominal leg at all, so it is outside classification rather than
                // missing it, and no unclassified total ever held it.
                isUncategorized = transaction.matches(SpendingSubject.Uncategorized),
                installment = lookup.installmentLabelOf(transaction),
                installmentId = transaction.installmentId,
                recurringId = transaction.recurringId,
                recurringCycle = transaction.recurringCycle,
                appliedRate = transaction.appliedRate(),
                perimeter = AgentPerimeter(
                    covers = "This posting alone, with every leg of it that holds money.",
                    excludes = listOf(
                        "the counterpart legs that explain WHY money moved — the category, the " +
                            "reconciliation, the conversion residue — which hold no money the " +
                            "user recognises as his",
                        "anything else of the same instalment plan or recurring template: this " +
                            "is one posting of it",
                    ),
                    seeAlso = listOf(
                        McpToolName.LIST_TRANSACTIONS.wireName,
                        McpToolName.GET_INVOICE.wireName,
                    ),
                ),
            ),
        )
    }

    private fun Entry.toAgentLeg(
        transaction: Transaction,
        invoicesByDimension: Map<Long, Invoice>,
    ): AgentLeg {
        val isLiability = account.type == AccountType.LIABILITY

        return AgentLeg(
            kind = if (isLiability) "card" else "account",
            name = account.name,
            accountId = account.id,
            // The ledger's own derivation, off the leg's sign with the adjustment override — the
            // same one the item surface reads, so a leg cannot say one thing here and another in a
            // listing of the account it landed on.
            direction = deriveTransactionType(amount, transaction.entries).name.lowercase(),
            amount = AgentFigure.exact(amount / CENTS_PER_UNIT, currency),
            invoiceId = dimensionId?.takeIf { isLiability }?.let { invoicesByDimension[it]?.id },
        )
    }

    /**
     * The rate this operation applied, and `null` whenever it applied none: a single-currency
     * operation, or one with a single monetary leg — a card purchase has nothing to divide by.
     *
     * The two ends are the ledger's own: the leg the money left (`Transaction.primaryEntry`, the
     * one owner of "outgoing") and the monetary leg it entered. The direction is the write form's,
     * source → target, from the same [impliedRate] the user was shown while typing, so the number
     * read back is the number that was offered.
     */
    private fun Transaction.appliedRate(): AgentAppliedRate? {
        val out = primaryEntry?.takeIf { it.amount < 0 } ?: return null
        val into = monetaryEntries.firstOrNull { it.amount > 0 } ?: return null
        if (out.currency == into.currency) return null

        return impliedRate(
            sourceAmount = abs(out.amount) / CENTS_PER_UNIT,
            targetAmount = abs(into.amount) / CENTS_PER_UNIT,
        )?.let {
            AgentAppliedRate(from = out.currency, to = into.currency, rate = it)
        }
    }

    private companion object {
        const val CENTS_PER_UNIT = 100.0
    }
}
