package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionRegistration
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.DeleteTransactionUseCase
import com.neoutils.finsight.domain.usecase.RegisterTransactionUseCase
import com.neoutils.finsight.domain.usecase.UpdateTransactionUseCase
import com.neoutils.finsight.extension.deriveTransactionType
import com.neoutils.finsight.extension.isAccept
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.McpPermissionNotice
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.capability
import com.neoutils.finsight.mcp.surface.AgentFigure
import com.neoutils.finsight.mcp.surface.AgentInstallment
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.surface.AgentRemovalAnswer
import com.neoutils.finsight.mcp.surface.AgentTransactionWriteAnswer
import com.neoutils.finsight.mcp.surface.toAgentTransaction
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * The three tools that write a posting, and the one thing they share: none of them decides what a
 * filled form turns out to be.
 *
 * A form with more than one instalment is an instalment plan, a form marked as repeating opens a
 * template, and everything else is a plain posting — that dispatch belongs to
 * `RegisterTransactionUseCase`, which the app's own sheet calls, and no tool here writes a second
 * copy of it. Where a tool does read `installments > 1` it is doing the opposite job: refusing a
 * combination the domain does not model — instalments on an account, instalments beside a recurring
 * mark — which the sheet never has to refuse, because its selectors never offered it. Turning away
 * an ill-formed request is not deciding what a well-formed one becomes. The same goes for editing:
 * what the rewrite can express is `Transaction.editObstacle`'s answer, and it is the same answer
 * that decides whether the screen offers the action at all.
 */

/** The two directions a posting can be written in from a form. */
private val TRANSACTION_TYPES: Map<String, TransactionType> = mapOf(
    "expense" to TransactionType.EXPENSE,
    "income" to TransactionType.INCOME,
)

/** How a posting is described to the person who will read the activity log. */
private fun Transaction.asLogLine(): String {
    val where = monetaryEntries.firstOrNull()?.account?.name ?: "no account"
    return "${title ?: "untitled"}, $amount, on $where, $date"
}

/** The names a posting is answered with, resolved once for the tool's own answer. */
private suspend fun lookupFor(
    transactions: List<Transaction>,
    categoryRepository: ICategoryRepository,
    installmentRepository: IInstallmentRepository,
): TransactionFacadeLookup = TransactionFacadeLookup.of(
    categories = categoryRepository.getAllCategoriesIncludingClosed(),
    installments = installmentRepository.getAllInstallments()
        .filter { plan -> transactions.any { it.installmentId == plan.id } },
)

// ----------------------------------------------------------------------------------
// create_transaction
// ----------------------------------------------------------------------------------

/**
 * **Records a posting the user made**: an expense or an income, in an account or on a card.
 *
 * Its recorte, against the operations family: this is the form, so it records what was spent or
 * received. Moving money between the user's own accounts is `transfer`, settling a card is
 * `pay_invoice`, correcting a balance is `adjust_balance` — each of those posts two monetary legs or
 * an equity one, and the form expresses neither.
 */
internal class CreateTransactionTool(
    private val clock: Clock,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val categoryRepository: ICategoryRepository,
    private val installmentRepository: IInstallmentRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val registerTransaction: RegisterTransactionUseCase,
) : McpTool {

    override val name: String = McpToolName.CREATE_TRANSACTION.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Record a new expense or income, in an account or on a credit card. " +
            "Identifiers come from list_accounts, list_cards and list_categories: this tool " +
            "resolves no names. Give exactly one of account_id or card_id; a card takes " +
            "expenses only. " +
            "PERIMETER: it records what the user spent or received. It does not move money " +
            "between the user's own accounts (transfer), pay a card bill (pay_invoice) or " +
            "correct a balance (adjust_balance) — those post two legs and are refused here. " +
            "With installments above 1 the purchase is split across the following invoices and " +
            "the answer carries every posting written; with is_recurring a monthly template is " +
            "opened and this posting is its first cycle. The two are mutually exclusive and " +
            "giving both is refused: instalments are already a repetition."

    override val inputSchema = schema(
        "type" to choice("Whether money went out or came in.", TRANSACTION_TYPES.keys.toList()),
        "amount" to amount(
            "How much, in the currency of the account or card it posts to — 45.90, not 4590. " +
                "Always positive: `type` says the direction.",
        ),
        "date" to text("The day it happened, as `2026-03-14`. Defaults to today, and is never in the future."),
        "title" to text("What it was. Required unless a category is given."),
        "category_id" to number("The category to classify it under, from list_categories."),
        "account_id" to number("The account the money moved in, from list_accounts."),
        "card_id" to number("The card it was charged to, from list_cards. Expenses only."),
        "invoice_month" to text(
            "Which invoice a card purchase lands on, as `2026-04` — the month it falls due. " +
                "Defaults to the card's open invoice.",
        ),
        "installments" to number("How many instalments to split a card purchase into. Defaults to 1."),
        "is_recurring" to yesOrNo(
            "Open a monthly template repeating on this day, with this posting as its first cycle. " +
                "Defaults to false.",
        ),
        required = listOf("type", "amount"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val type = TRANSACTION_TYPES.getValue(
            arguments.requiredOneOf("type", TRANSACTION_TYPES.keys.toList()),
        )
        val amount = arguments.requiredMoney("amount")
        val date = arguments.date("date") ?: clock.today()
        val title = arguments.string("title")
        val installments = arguments.count("installments", default = 1, max = MAX_INSTALLMENTS, min = 1)
        val isRecurring = arguments.flag("is_recurring", default = false)

        val account = arguments.long("account_id")?.let { accountRepository.require(it) }
        val card = arguments.long("card_id")?.let { creditCardRepository.require(it) }
        val category = arguments.long("category_id")?.let { categoryRepository.require(it) }

        val summary = summaryOf(type, amount, title, category, account, card, installments)

        if (account != null && card != null) {
            return@writing refusedWith(
                AgentRefusal(
                    reason = "A posting sits in one place: give `account_id` or `card_id`, not both.",
                ),
                summary = summary,
            )
        }

        // `TransactionForm.from` normalises by dropping what does not fit, and that is right for
        // the sheet: its selectors never offered the combination, so the drop takes nothing the
        // user chose. Every argument here was declared instead of offered, so the same drop answers
        // "Recorded." for a split or a classification that never reached the ledger — and, for a
        // card an income cannot go on, a refusal naming the `account_id` nobody gave.
        if (type.isIncome && card != null) {
            return@writing refusedWith(
                AgentRefusal(
                    reason = "A card takes expenses only: with `type` income, give `account_id` " +
                        "and not `card_id`.",
                ),
                summary = summary,
            )
        }

        if (installments > 1 && account != null) {
            return@writing refusedWith(
                AgentRefusal(
                    reason = "Instalments are a card affordance: give `installments` above 1 with " +
                        "a `card_id`, not with an `account_id`.",
                ),
                summary = summary,
            )
        }

        // The sheet drops the recurring mark when a purchase is split, and says why: paying in
        // instalments is already a repetition. It can drop it silently because it also stops
        // showing it. Nothing was ever shown here, so the same drop would be a template the
        // caller asked for, never opened, and reported as done.
        if (isRecurring && installments > 1) {
            return@writing refusedWith(
                AgentRefusal(
                    reason = "Instalments are already a repetition: give `installments` above 1 " +
                        "or `is_recurring`, not both.",
                ),
                summary = summary,
            )
        }

        if (category != null && !category.type.isAccept(type)) {
            return@writing refusedWith(
                AgentRefusal(
                    reason = "`category_id` names \"${category.name}\", an " +
                        "${category.type.name.lowercase()} category, and `type` is " +
                        "${type.name.lowercase()}: a category classifies one direction only. " +
                        "Give an ${type.name.lowercase()} category, or leave `category_id` out.",
                ),
                summary = summary,
            )
        }

        val form = TransactionForm.from(
            type = type,
            amount = amount.asFormAmount(),
            title = title,
            date = date.asFormDate(),
            category = category,
            target = if (card != null) TransactionTarget.CREDIT_CARD else TransactionTarget.ACCOUNT,
            creditCard = card,
            // Absent means the invoice the card is on right now, which is what the app's own
            // sheet pre-selects; the build step resolves or opens the cycle either way.
            invoiceDueMonth = arguments.monthOrNull("invoice_month")
                ?: card?.let { invoiceRepository.getOpenInvoice(it.id)?.dueMonth },
            account = account,
            installments = installments,
        )

        registerTransaction(form, isRecurring).fold(
            ifLeft = { refusedBy(it, summary) },
            ifRight = { registration -> registration.answered(title, card, summary) },
        )
    }

    private suspend fun TransactionRegistration.answered(
        title: String?,
        card: CreditCard?,
        summary: String,
    ) = run {
        val lookup = lookupFor(transactions, categoryRepository, installmentRepository)
        val plan = transactions.firstNotNullOfOrNull { it.installmentId }
            ?.let { installmentRepository.getInstallmentById(it) }

        applied(
            payload = AgentTransactionWriteAnswer(
                transaction = transactions.first().toAgentTransaction(lookup = lookup)!!,
                // Only when there is more than one: a single posting is already the field
                // above it, and repeating it would read as two things having been written.
                transactions = transactions
                    .takeIf { it.size > 1 }
                    ?.mapNotNull { it.toAgentTransaction(lookup = lookup) }
                    .orEmpty(),
                installment = plan?.let {
                    AgentInstallment(
                        id = it.id,
                        title = title,
                        card = card?.name,
                        cardId = card?.id,
                        count = it.count,
                        total = card?.currency?.let { currency ->
                            AgentFigure.exact(it.totalAmount, currency)
                        },
                    )
                },
                note = when (this) {
                    is TransactionRegistration.Installments ->
                        "Recorded as ${transactions.size} instalments, one per invoice they land on."

                    is TransactionRegistration.Single ->
                        "Recorded." + if (transaction.recurringId != null) {
                            " A monthly template was opened with it as its first cycle."
                        } else {
                            ""
                        }
                },
            ),
            summary = summary,
            reference = reference(
                AgentActivity.Reference.Kind.TRANSACTION,
                transactions.first().id,
            ),
        )
    }

    private fun summaryOf(
        type: TransactionType,
        amount: Double,
        title: String?,
        category: Category?,
        account: Account?,
        card: CreditCard?,
        installments: Int,
    ): String = buildString {
        append(type.name.lowercase())
        append(' ')
        append(amount)
        append(" — ")
        append(title ?: category?.name ?: "untitled")
        append(" on ")
        append(account?.name ?: card?.name ?: "no account")
        if (installments > 1) append(" in $installments instalments")
    }

    private companion object {

        /**
         * A ceiling on the split, clamped like every count on this surface rather than refused: the
         * domain's rule is a count of at least one, and anything past thirty years of monthly
         * instalments is a typing accident with no answer worth a round trip.
         */
        const val MAX_INSTALLMENTS = 360
    }
}

// ----------------------------------------------------------------------------------
// update_transaction
// ----------------------------------------------------------------------------------

/**
 * **Edits a posting the user already made.**
 *
 * The rewrite is total: `UpdateTransactionUseCase` deletes the old legs and rebuilds from the edited
 * form, which describes an expense or an income and nothing else. A transfer, a card payment, an
 * adjustment and one share of an instalment plan are refused *there*, in the words of the domain,
 * and nothing of that rule is restated here — it is the same derivation that stops the app's own
 * screen from offering the edit.
 *
 * Every field is carried rather than patched: what the call does not name is taken from the posting
 * as the ledger holds it now, so changing only the amount cannot blank the title by omission.
 */
internal class UpdateTransactionTool(
    private val transactionRepository: ITransactionRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val categoryRepository: ICategoryRepository,
    private val installmentRepository: IInstallmentRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val updateTransaction: UpdateTransactionUseCase,
) : McpTool {

    override val name: String = McpToolName.UPDATE_TRANSACTION.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Change an expense or an income that was already recorded — its amount, title, date, " +
            "category, or where it posts. What is not given keeps the value it already has. " +
            "PERIMETER: only a posting with a single monetary leg can be edited, because the " +
            "edit rewrites it from that leg. A transfer and a card payment have two monetary " +
            "legs and are refused; so are an adjustment (adjust_balance, adjust_invoice) and " +
            "one share of an instalment plan (update_installment). " +
            "An amount must be greater than zero: zero or less is refused, because it is not " +
            "the removal it imitates. Removal is " +
            "delete_transaction, on the ${McpToolName.DELETE_TRANSACTION.axis.capability} " +
            "capability."

    override val inputSchema = schema(
        "id" to number("The posting to edit, from list_transactions."),
        "type" to choice("Whether money went out or came in.", TRANSACTION_TYPES.keys.toList()),
        "amount" to amount("The new amount, in the currency it posts in — 45.90, not 4590."),
        "date" to text("The new day, as `2026-03-14`."),
        "title" to text("The new title."),
        "category_id" to number("The category to classify it under, from list_categories."),
        "account_id" to number("Move it to this account, from list_accounts."),
        "card_id" to number("Move it to this card, from list_cards."),
        "invoice_month" to text("Move a card posting to the invoice falling due in this month, as `2026-04`."),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")

        val stored = transactionRepository.getTransactionById(id)
            ?: return@writing refusedWith(
                AgentRefusal.notFound("transaction", id),
                summary = "edit transaction $id",
            )

        val summary = stored.asLogLine()

        // Zeroing an amount is the contortion a withheld removal invites, and it is worse than
        // the refusal it stands in for: the posting leaves every total and stays in every
        // listing and count. Refused here, and named as what it was reaching for.
        //
        // A negative amount is the same contortion by another route, and a worse one: what an
        // expense of minus forty means to a double-entry ledger is an income of forty, so the
        // posting does not merely leave the total — it moves to the other side of it. The domain
        // refuses both, and this guard is not a second copy of that rule: it is here because only
        // the tool can name `delete_transaction` as what the caller was working around.
        val amount = arguments.money("amount")
        if (amount != null && amount <= 0.0) {
            return@writing refusedWith(
                AgentRefusal(
                    reason = "An amount of ${if (amount == 0.0) "zero" else "less than zero"} " +
                        "would leave the posting in the history and out of every total, which is " +
                        "not the removal it imitates. Removing it is " +
                        "`${McpToolName.DELETE_TRANSACTION.wireName}`, on the " +
                        "${McpToolName.DELETE_TRANSACTION.axis.capability} capability — and where " +
                        "that capability is withheld, ${McpPermissionNotice.WHERE_TO_GRANT}.",
                    tryInstead = McpToolName.DELETE_TRANSACTION.wireName,
                ),
                summary = summary,
            )
        }

        val namedAccount = arguments.long("account_id")
        val namedCard = arguments.long("card_id")

        if (namedAccount != null && namedCard != null) {
            return@writing refusedWith(
                AgentRefusal(
                    reason = "A posting sits in one place: give `account_id` or `card_id`, not both.",
                ),
                summary = summary,
            )
        }

        val storedCard = stored.liabilityAccountId?.let { accountId ->
            creditCardRepository.getAllCreditCardsIncludingClosed()
                .firstOrNull { it.accountId == accountId }
        }

        val card = namedCard?.let { creditCardRepository.require(it) }
            ?: storedCard.takeIf { namedAccount == null }

        val account = namedAccount?.let { accountRepository.require(it) }
            ?: stored.sourceAccount.takeIf { card == null }

        val category = arguments.long("category_id")?.let { categoryRepository.require(it) }
            ?: stored.nominalDimensionId?.let { categoryRepository.getCategoryByDimensionId(it) }

        val form = TransactionForm.from(
            type = arguments.oneOf("type", TRANSACTION_TYPES.keys.toList())
                ?.let { TRANSACTION_TYPES.getValue(it) }
                ?: stored.storedType(),
            amount = (amount ?: stored.amount).asFormAmount(),
            title = arguments.string("title") ?: stored.title,
            date = (arguments.date("date") ?: stored.date).asFormDate(),
            category = category,
            target = if (card != null) TransactionTarget.CREDIT_CARD else TransactionTarget.ACCOUNT,
            creditCard = card,
            invoiceDueMonth = arguments.monthOrNull("invoice_month")
                ?: stored.liabilityDimensionId?.let { dimensionId ->
                    invoiceRepository.getAllInvoices().firstOrNull { it.dimensionId == dimensionId }
                }?.dueMonth
                ?: card?.let { invoiceRepository.getOpenInvoice(it.id)?.dueMonth },
            account = account,
        )

        updateTransaction(id, form).fold(
            ifLeft = { refusedBy(it, summary) },
            ifRight = { updated ->
                val lookup = lookupFor(listOf(updated), categoryRepository, installmentRepository)
                applied(
                    payload = AgentTransactionWriteAnswer(
                        transaction = updated.toAgentTransaction(lookup = lookup)!!,
                        note = "Edited. Everything the call did not name kept the value it had.",
                    ),
                    summary = summary,
                    reference = reference(AgentActivity.Reference.Kind.TRANSACTION, updated.id),
                )
            },
        )
    }

    /** Which way the posting already went, as the ledger derives it from its own monetary leg. */
    private fun Transaction.storedType(): TransactionType = monetaryEntries.firstOrNull()
        ?.let { deriveTransactionType(it.amount, entries) }
        ?: TransactionType.EXPENSE
}

// ----------------------------------------------------------------------------------
// delete_transaction
// ----------------------------------------------------------------------------------

/** **Removes a posting and the ledger entries behind it.** */
internal class DeleteTransactionTool(
    private val transactionRepository: ITransactionRepository,
    private val deleteTransaction: DeleteTransactionUseCase,
) : McpTool {

    override val name: String = McpToolName.DELETE_TRANSACTION.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Remove a posting for good, together with the ledger entries behind it. " +
            "PERIMETER: it removes one posting. A posting on an archived account or card is " +
            "refused — taking its movement away would give that account a balance again — and " +
            "so is one on a paid invoice, which is settled history. Removing a whole instalment " +
            "plan is delete_installment, not this called N times."

    override val inputSchema = schema(
        "id" to number("The posting to remove, from list_transactions."),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")

        // Read before the removal so the log can say what went, in the words it was known by:
        // afterwards there is nothing left to describe it with.
        val stored = transactionRepository.getTransactionById(id)
            ?: return@writing refusedWith(
                AgentRefusal.notFound("transaction", id),
                summary = "delete transaction $id",
            )

        deleteTransaction(id).reported(
            summary = stored.asLogLine(),
            payload = {
                AgentRemovalAnswer(
                    removed = "transaction",
                    id = id,
                    name = stored.title,
                    note = "Removed, with its ledger entries.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.TRANSACTION, id) },
        )
    }
}
