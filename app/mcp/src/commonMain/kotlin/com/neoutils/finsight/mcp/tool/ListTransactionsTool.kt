@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.matches
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.nominalLeg
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.mcp.contract.AssumedDefaults
import com.neoutils.finsight.mcp.contract.CivilDateRange
import com.neoutils.finsight.mcp.contract.Cursor
import com.neoutils.finsight.mcp.contract.DisplaySign
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
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The name of the transaction listing, named by the aggregation tool and the prompts. */
const val LIST_TRANSACTIONS_TOOL: String = "${TOOL_NAME_PREFIX}list_transactions"

/**
 * Which of a transaction's dates a period cuts on.
 *
 * A card purchase has two: the day it was made and the invoice it landed in. The filter
 * has to say which one it used, or a consumer reading "March" cannot tell a purchase made
 * in March from one billed in March.
 */
enum class TransactionDateField {

    /**
     * The day the transaction happened — the only cut the reading owner offers, and the
     * one this surface applies. The invoice is reached by identifier instead
     * (`invoiceId`), which is the exact cut and needs no date arithmetic.
     */
    TRANSACTION_DATE,
}

/** The three states the category filter distinguishes. */
enum class CategoryFilter {

    /** Every transaction, classified or not — the default. */
    ANY,

    /** Only those classified in the category named by `categoryId`. */
    CATEGORY,

    /**
     * Only those with a nominal leg carrying **no** dimension.
     *
     * The absence of a classification, never a bucket: a transfer, a card payment and an
     * adjustment have no nominal leg at all, so they are outside the axis rather than
     * unclassified, and no unclassified total ever contained them. The predicate is the
     * domain's own (`Transaction.matches`), shared with every screen that offers this cut.
     */
    UNCATEGORIZED,
}

/** The refusals this listing can produce beyond the common ones. */
internal object ListTransactionsCodes {

    /** Two account-shaped filters were given, and the reading cuts by one account. */
    const val AMBIGUOUS_ACCOUNT_FILTER: String = "AMBIGUOUS_ACCOUNT_FILTER"

    /** `categoryId` is required by, and only meaningful for, the CATEGORY filter. */
    const val CATEGORY_FILTER_MISMATCH: String = "CATEGORY_FILTER_MISMATCH"

    /** The amount range is empty — its floor is above its ceiling. */
    const val EMPTY_AMOUNT_RANGE: String = "EMPTY_AMOUNT_RANGE"

    val all: Set<String> = setOf(AMBIGUOUS_ACCOUNT_FILTER, CATEGORY_FILTER_MISMATCH, EMPTY_AMOUNT_RANGE)
}

/**
 * The user's transactions, cut by period, account, card, invoice, category and amount.
 *
 * The period cut belongs to the reading's owner (`ITransactionRepository.getTransactionsBy`),
 * and so does the account and the dimension. What this tool adds is the vocabulary a
 * consumer speaks in — a card is its `LIABILITY` account, an invoice is a dimension — and
 * the two cuts the reading owner does not express: being unclassified, and a range of
 * amounts. Both are applied with the domain's own definitions
 * (`Transaction.matches`, `Transaction.amount`), which are the same ones the app's own
 * screens filter with.
 */
class ListTransactionsTool(
    private val transactions: ITransactionRepository,
    private val accounts: IAccountRepository,
    private val creditCards: ICreditCardRepository,
    private val invoices: IInvoiceRepository,
    private val categories: ICategoryRepository,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : McpTool {

    override val name: String = LIST_TRANSACTIONS_TOOL

    override val title: String = "List transactions"

    override val description: String = """
        The user's transactions, one page at a time.

        **For any total, use $AGGREGATE_TRANSACTIONS_TOOL.** Paging this listing and
        adding the amounts up produces a number that looks exact and is not: it ignores
        the currency of each account, and it counts as spending what the domain does not
        classify as spending. `$AGGREGATE_TRANSACTIONS_TOOL` computes over the whole
        period on the server.

        Each item carries the identifier **and** the name of its account, card, invoice
        and category, so no name ever has to be resolved in a second call.

        A card purchase carries **two dates**: `date`, the day it was made, and
        `invoice.dueDate`, the bill it landed in. The period cuts on `date`, and the
        response says so in `filteredOn`. To read one bill, pass `invoiceId`.

        `amount` is signed for display: spending negative, income positive, everywhere on
        this surface.

        The period, the reference date and the archived scope this call assumed all come
        back in `assumed`.
    """.trimIndent()

    override val inputSchema: JsonObject = objectSchema {
        pagingProperties()
        stringProperty("startDate", "Inclusive, YYYY-MM-DD. Cuts on the transaction's own date.")
        stringProperty("endDate", "Inclusive, YYYY-MM-DD. Cuts on the transaction's own date.")
        integerProperty("accountId", "Only transactions with a leg on this account.")
        integerProperty("creditCardId", "Only this card's transactions. A card is its LIABILITY account, not a filter of its own.")
        integerProperty("invoiceId", "Only the transactions billed in this invoice.")
        enumProperty(
            name = "category",
            values = CategoryFilter.entries.map { it.name },
            description = "ANY (default), CATEGORY with `categoryId`, or UNCATEGORIZED — the absence of a classification.",
        )
        integerProperty("categoryId", "Required by, and only valid with, category=CATEGORY.")
        numberProperty("minAmount", "Floor of the transaction's magnitude, in the major unit.")
        numberProperty("maxAmount", "Ceiling of the transaction's magnitude, in the major unit.")
    }

    override val outputSchema: JsonObject = toolOutcomeSchema(
        resultSchema = objectSchema(required = listOf("transactions", "totalMatching", "filteredOn", "assumed")) {
            arrayProperty("transactions", transactionSchema, "One page of transactions, newest first.")
            enumProperty(
                name = "filteredOn",
                values = TransactionDateField.entries.map { it.name },
                description = "Which of a transaction's dates the period cut on.",
            )
            pagingResultProperties()
            objectProperty("assumed", assumedSchema)
        },
        errorCodes = CommonToolCodes.all + ListTransactionsCodes.all +
            ResponseLimits.CODE_PAGE_LIMIT_ABOVE_CEILING +
            ResponseLimits.CODE_PAGE_LIMIT_NOT_POSITIVE +
            AssumedDefaults.CODE_NOT_A_CIVIL_DATE,
    )

    override val annotations: ToolAnnotations = ToolAnnotations(readOnlyHint = true)

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        val args = Arguments(arguments)
        val startDate = args.date("startDate")
        val endDate = args.date("endDate")
        val accountId = args.long("accountId")
        val creditCardId = args.long("creditCardId")
        val invoiceId = args.long("invoiceId")
        val categoryFilter = args.enum("category", CategoryFilter.entries.toTypedArray()) ?: CategoryFilter.ANY
        val categoryId = args.long("categoryId")
        val minAmount = args.double("minAmount")
        val maxAmount = args.double("maxAmount")
        val requestedLimit = args.int("limit")
        val cursor = args.string("cursor")?.let(::Cursor)
        args.failure?.let { return ToolOutcome.Failed(it) }

        if (accountId != null && creditCardId != null) {
            return ToolOutcome.Failed(
                ToolError.invalidInput(
                    code = ListTransactionsCodes.AMBIGUOUS_ACCOUNT_FILTER,
                    message = "`accountId` and `creditCardId` both name the account a leg posts to, " +
                        "and the reading cuts by one. Pass whichever of the two you mean.",
                ),
            )
        }

        if ((categoryFilter == CategoryFilter.CATEGORY) != (categoryId != null)) {
            return ToolOutcome.Failed(
                ToolError.invalidInput(
                    code = ListTransactionsCodes.CATEGORY_FILTER_MISMATCH,
                    message = "category=CATEGORY requires `categoryId`, and `categoryId` is meaningless " +
                        "with any other category filter.",
                ),
            )
        }

        if (minAmount != null && maxAmount != null && minAmount > maxAmount) {
            return ToolOutcome.Failed(
                ToolError.invalidInput(
                    code = ListTransactionsCodes.EMPTY_AMOUNT_RANGE,
                    message = "`minAmount` ($minAmount) is above `maxAmount` ($maxAmount); no amount satisfies it.",
                ),
            )
        }

        val limit = when (val resolved = resolvePageLimit(requestedLimit)) {
            is PageLimit.Refused -> return ToolOutcome.Failed(resolved.error)
            is PageLimit.Accepted -> resolved.limit
        }

        val card = creditCardId?.let { id ->
            creditCards.getCreditCardById(id) ?: return ToolOutcome.Failed(
                ToolError.notFound(CommonToolCodes.NOT_FOUND, "No credit card with id $id"),
            )
        }

        val invoice = invoiceId?.let { id ->
            invoices.getInvoiceById(id) ?: return ToolOutcome.Failed(
                ToolError.notFound(CommonToolCodes.NOT_FOUND, "No invoice with id $id"),
            )
        }

        val category = categoryId?.let { id ->
            categories.getCategoryById(id) ?: return ToolOutcome.Failed(
                ToolError.notFound(CommonToolCodes.NOT_FOUND, "No category with id $id"),
            )
        }

        val assumed = AssumedDefaults.resolve(
            today = clock.today(timeZone),
            timeZone = timeZone,
            period = if (startDate != null && endDate != null) CivilDateRange(startDate, endDate) else null,
        )

        // The period, the account and the dimension are the reading owner's cuts, so they
        // travel down rather than being applied to what it answered. The invoice's
        // dimension takes precedence over the category's because it is the narrower one;
        // when it is used, the category cut falls to the predicate below.
        val dimensionId = invoice?.dimensionId ?: category?.dimensionId
        val matching = transactions.getTransactionsBy(
            startDate = startDate,
            endDate = endDate,
            dimensionId = dimensionId,
            accountId = accountId ?: card?.accountId,
        ).filter { transaction ->
            transaction.matchesCategoryFilter(categoryFilter, category, appliedByReader = dimensionId == category?.dimensionId) &&
                transaction.matchesAmountRange(minAmount, maxAmount)
        }

        val page = paginate(matching, limit, cursor) { "${it.date}|${it.id}" }

        val context = TransactionContext.of(accounts, creditCards, invoices, categories)
        val items = page.items.map { transaction ->
            buildJsonObject { putTransaction(transaction, context) }
        }

        return ok {
            putPage("transactions", page.with(items))
            put("filteredOn", TransactionDateField.TRANSACTION_DATE.name)
            putAssumed(assumed)
        }
    }
}

/**
 * Whether this transaction satisfies the category cut.
 *
 * `ANY` matches everything, and `CATEGORY` matches nothing extra when the reading owner
 * already cut by that dimension — the predicate then only has to not undo it.
 */
private fun Transaction.matchesCategoryFilter(
    filter: CategoryFilter,
    category: Category?,
    appliedByReader: Boolean,
): Boolean = when (filter) {
    CategoryFilter.ANY -> true
    CategoryFilter.UNCATEGORIZED -> matches(SpendingSubject.Uncategorized)
    CategoryFilter.CATEGORY -> appliedByReader || (category != null && matches(SpendingSubject.Categorized(category)))
}

/** Whether this transaction's magnitude falls in the range asked for. */
private fun Transaction.matchesAmountRange(min: Double?, max: Double?): Boolean =
    (min == null || amount >= min) && (max == null || amount <= max)

/**
 * The facades a transaction's legs are resolved into, gathered once per call.
 *
 * A transaction carries identities and no facade (the ledger knows none), so a card, an
 * invoice and a category are looked up from the leg that carries their key. Doing it per
 * row would be one query per line of the page.
 */
internal class TransactionContext(
    private val accountsById: Map<Long, com.neoutils.finsight.domain.model.Account>,
    private val cardsByAccountId: Map<Long, CreditCard>,
    private val invoicesByDimensionId: Map<Long, Invoice>,
    private val categoriesByDimensionId: Map<Long, Category>,
) {

    fun account(id: Long?) = id?.let(accountsById::get)

    fun card(accountId: Long?) = accountId?.let(cardsByAccountId::get)

    fun invoice(dimensionId: Long?) = dimensionId?.let(invoicesByDimensionId::get)

    fun category(dimensionId: Long?) = dimensionId?.let(categoriesByDimensionId::get)

    companion object {
        suspend fun of(
            accounts: IAccountRepository,
            creditCards: ICreditCardRepository,
            invoices: IInvoiceRepository,
            categories: ICategoryRepository,
        ) = TransactionContext(
            // Closed ones included, deliberately: history keeps referring to an account,
            // a card and a category that were later retired, and a listing that could not
            // name them would answer with an identifier and no name.
            accountsById = accounts.getAllAccountsIncludingClosed().associateBy { it.id },
            cardsByAccountId = creditCards.getAllCreditCardsIncludingClosed().associateBy { it.accountId },
            invoicesByDimensionId = invoices.getAllInvoices().mapNotNull { invoice ->
                invoice.dimensionId?.let { it to invoice }
            }.toMap(),
            categoriesByDimensionId = categories.getAllCategoriesIncludingClosed().associateBy { it.dimensionId },
        )
    }
}

/**
 * One transaction, as this surface states it: what it is, what it moved, and the facades
 * it touched — never its legs.
 *
 * **No ledger leg crosses this boundary**, and no debit-positive amount either. What the
 * transaction *is* comes back as the label the domain derived
 * (`deriveTransactionLabel`), which no tool accepts as input.
 */
internal fun JsonObjectBuilder.putTransaction(transaction: Transaction, context: TransactionContext) {
    put("id", transaction.id)
    transaction.title?.let { put("title", it) }
    put("date", transaction.date.toString())
    put("label", transaction.label.name)
    put("amount", ToolJson.encodeToJsonElement(transaction.displayAmount()))

    context.account(transaction.sourceAccount?.id)?.let { putAccountRef("account", it) }
    context.card(transaction.liabilityAccountId)?.let { putRef("creditCard", it.id, it.name) }

    context.invoice(transaction.liabilityDimensionId)?.let { invoice ->
        putJsonObject("invoice") {
            put("id", invoice.id)
            put("dueMonth", invoice.dueMonth.toString())
            put("dueDate", invoice.dueDate.toString())
            put("status", invoice.status.name)
        }
    }

    val category = context.category(transaction.nominalDimensionId)
    if (category != null) {
        putRef("category", category.id, category.name)
    } else {
        // The absence of a dimension on the nominal leg, which is what being
        // unclassified *is* — never a category with a name.
        put("isUncategorized", transaction.matches(SpendingSubject.Uncategorized))
    }

    transaction.installmentId?.let { id ->
        putJsonObject("installment") {
            put("id", id)
            transaction.installmentNumber?.let { put("number", it) }
        }
    }

    transaction.recurringId?.let { id ->
        putJsonObject("recurring") {
            put("id", id)
            transaction.recurringCycle?.let { put("cycle", it) }
        }
    }
}

/**
 * The transaction's amount, with the **display** sign this whole surface reads with.
 *
 * It is read off one leg and one only, chosen by the same rule `DisplaySign` states: a
 * transaction with a nominal leg is reported from the money's side — an expense leaves,
 * so it is negative, and an income arrives, so it is positive — and one without a nominal
 * leg (a transfer, a card payment, an adjustment) is reported from the monetary leg the
 * money left, with that account type's own display sign.
 *
 * The ledger's debit-positive convention reaches nothing here. Letting it out would have
 * an agent report a month of spending as income.
 */
internal fun Transaction.displayAmount(): MoneyAmount {
    val leg = entries.nominalLeg() ?: primaryEntry
    val sign = leg?.account?.type?.let(DisplaySign::of) ?: DisplaySign.ofMoneyHeld
    val value = (leg?.amount ?: 0L) / 100.0

    return MoneyAmount.of(
        value = sign * value,
        currency = leg?.currency ?: primaryEntry?.currency.orEmpty(),
    )
}

internal val transactionSchema: JsonObject = objectSchema(required = listOf("id", "date", "label", "amount")) {
    integerProperty("id", "The opaque identifier.")
    stringProperty("title", "What the user wrote, when they wrote anything.")
    stringProperty("date", "The day the transaction happened — the date the period cuts on.")
    enumProperty(
        name = "label",
        values = TransactionLabel.entries.map { it.name },
        description = "Derived by the domain from the account natures of the legs. No tool accepts it as input.",
    )
    objectProperty("amount", moneyAmountSchema)
    objectProperty("account", refSchema("The account the money left, when it left one."))
    objectProperty("creditCard", refSchema("The card, when the transaction posts to one."))
    objectProperty(
        name = "invoice",
        schema = objectSchema(required = listOf("id", "dueMonth")) {
            integerProperty("id", "The opaque identifier of the bill this landed in.")
            stringProperty("dueMonth", "The month the bill falls due, YYYY-MM.")
            stringProperty("dueDate", "The day the bill falls due — the second date of a card purchase.")
            stringProperty("status", "FUTURE, OPEN, CLOSED, PAID or RETROACTIVE.")
        },
    )
    objectProperty("category", refSchema("The category it is classified in."))
    booleanProperty(
        name = "isUncategorized",
        description = "Present without `category`: true means a nominal leg carrying no dimension — " +
            "unclassified. False means the transaction is outside the classification axis altogether.",
    )
    objectProperty(
        name = "installment",
        schema = objectSchema(required = listOf("id")) {
            integerProperty("id", "The installment plan this transaction is one payment of.")
            integerProperty("number", "Which payment of the plan this is.")
        },
    )
    objectProperty(
        name = "recurring",
        schema = objectSchema(required = listOf("id")) {
            integerProperty("id", "The recurring template that produced it.")
            integerProperty("cycle", "Which cycle of the template.")
        },
    )
}
