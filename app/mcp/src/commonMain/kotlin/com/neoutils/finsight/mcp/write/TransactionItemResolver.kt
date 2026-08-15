@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.write

import arrow.core.Either
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.error.TransferException
import com.neoutils.finsight.domain.error.UnbalancedTransactionException
import com.neoutils.finsight.domain.exception.AccountNotAdjustedException
import com.neoutils.finsight.domain.exception.BuildTransactionException
import com.neoutils.finsight.domain.exception.InvoiceNotAdjustedException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.dueMonthFor
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.model.invoiceWindowOn
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCase
import com.neoutils.finsight.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finsight.domain.usecase.AdjustInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CreateTransactionUseCase
import com.neoutils.finsight.domain.usecase.PayInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finsight.extension.isAccept
import com.neoutils.finsight.mcp.contract.CivilDate
import com.neoutils.finsight.mcp.contract.MONEY_SCALE
import com.neoutils.finsight.mcp.contract.ToolError
import com.neoutils.finsight.mcp.contract.parseCivilDate
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.math.abs
import kotlin.time.ExperimentalTime

/**
 * The seven intents this surface writes with.
 *
 * They are **intents**, never ledger postings: no tool accepts a leg, a signed amount or
 * an account of the chart by nature, and none accepts the transaction's label — what a
 * transaction *is* is derived from the account natures of its legs, and accepting the
 * declaration would create a second source of truth that diverges at the first edge case.
 */
enum class WriteIntent {

    /** Money leaving one of the user's accounts. */
    EXPENSE,

    /** Money arriving in one of the user's accounts. */
    INCOME,

    /**
     * A purchase on a card, in one payment or in several.
     *
     * **In several, it is still one operation**: `AddInstallmentUseCase` writes every
     * payment as one unit of work, because an installment is a single decision by the
     * user and writing 7 of 12 would leave a plan describing money that was never
     * recorded.
     */
    CARD_PURCHASE,

    /**
     * Money moving between two of the user's own accounts.
     *
     * Across currencies the call states **both ends** and no rate: that is what the
     * statement shows, and the rate is the quotient of the two, derived and archived by
     * the domain after the write.
     */
    TRANSFER,

    /** Settling a card's bill from one of the user's accounts. */
    INVOICE_PAYMENT,

    /** Restating what an account holds on a date. */
    ACCOUNT_ADJUSTMENT,

    /** Restating what an invoice owes on a date. The same mechanism as the above. */
    INVOICE_ADJUSTMENT,
}

/** Why an item could not be turned into a call of the use case that owns it. */
object WriteItemCodes {

    /** The item names no intent, or one outside the enumeration. */
    const val UNKNOWN_INTENT: String = "UNKNOWN_INTENT"

    /** A field this intent requires is absent. */
    const val MISSING_FIELD: String = "MISSING_FIELD"

    /** A field is present but does not describe what it must. */
    const val INVALID_FIELD: String = "INVALID_FIELD"

    /** An identifier named nothing. */
    const val ACCOUNT_NOT_FOUND: String = "ACCOUNT_NOT_FOUND"

    const val CREDIT_CARD_NOT_FOUND: String = "CREDIT_CARD_NOT_FOUND"

    const val INVOICE_NOT_FOUND: String = "INVOICE_NOT_FOUND"

    /**
     * The item named a category that does not exist.
     *
     * It is **invalid input, and no category is created**: an agent that created one
     * while importing a statement would produce a variation of the same category with
     * every statement, and undoing that is expensive. The refusal names the identifier
     * that was asked for.
     */
    const val CATEGORY_NOT_FOUND: String = "CATEGORY_NOT_FOUND"

    /** A money value's currency is not the one the account it posts to declares. */
    const val CURRENCY_MISMATCH: String = "CURRENCY_MISMATCH"

    /** A name, a label or free text was passed where an identifier belongs. */
    const val NAME_IS_NOT_AN_IDENTIFIER: String = "NAME_IS_NOT_AN_IDENTIFIER"

    /**
     * The category named does not classify this direction — an income category on an
     * expense, or the reverse.
     *
     * Refused rather than dropped: `TransactionForm` discards a category the direction
     * does not accept, and letting that happen here would silently record the item
     * unclassified while the call looked like it succeeded.
     */
    const val CATEGORY_TYPE_MISMATCH: String = "CATEGORY_TYPE_MISMATCH"

    val all: Set<String> = setOf(
        UNKNOWN_INTENT,
        MISSING_FIELD,
        INVALID_FIELD,
        ACCOUNT_NOT_FOUND,
        CREDIT_CARD_NOT_FOUND,
        INVOICE_NOT_FOUND,
        CATEGORY_NOT_FOUND,
        CURRENCY_MISMATCH,
        NAME_IS_NOT_AN_IDENTIFIER,
        CATEGORY_TYPE_MISMATCH,
    )
}

/**
 * The refusals the **domain** produces, by family.
 *
 * They are families and not one code per rule, deliberately: the rules are sealed
 * hierarchies of the domain that grow without this module knowing, and a code list that
 * had to be kept in step would be wrong the first time one of them gained a case. What
 * stays stable is the family — which is what a consumer branches on — and the English
 * message, which names the exact rule that refused.
 */
object DomainRefusalCodes {

    /** The form does not describe a transaction that can be written. */
    const val BUILD_TRANSACTION: String = "DOMAIN_BUILD_TRANSACTION_REFUSED"

    /** A rule of transferring between accounts refused it. */
    const val TRANSFER: String = "DOMAIN_TRANSFER_REFUSED"

    /** A rule of the invoice lifecycle refused it — a closed bill, a paid one. */
    const val INVOICE: String = "DOMAIN_INVOICE_REFUSED"

    /** The ledger's write boundary refused it — a closed account, an unbalanced intent. */
    const val LEDGER: String = "DOMAIN_LEDGER_REFUSED"

    /** The balance already is the one that was asked for, so there is nothing to adjust. */
    const val NOTHING_TO_ADJUST: String = "NOTHING_TO_ADJUST"

    /** A refusal of the domain this surface has no family for. */
    const val OTHER: String = "DOMAIN_REFUSED"

    val all: Set<String> = setOf(BUILD_TRANSACTION, TRANSFER, INVOICE, LEDGER, NOTHING_TO_ADJUST, OTHER)
}

/**
 * One item of a write call, resolved: the use case that owns it, its arguments, and the
 * facades the identifiers named.
 *
 * It is deliberately **two steps**: resolving states what would be written and persists
 * nothing, and applying performs it. That is what lets the dry run and the write share one
 * answer to "which invoice does this purchase fall in" instead of having two.
 */
class ResolvedItem internal constructor(
    val intent: WriteIntent,
    val date: LocalDate,
    /** What would be written, as a payload — the resolved invoice and category included. */
    val preview: JsonObject,
    private val write: suspend () -> Either<ToolError, List<Transaction>>,
) {

    /** Performs the write. The transactions come back with the label the domain derived. */
    suspend fun apply(): Either<ToolError, List<Transaction>> = write()
}

/**
 * Turns an item of intent into a call of the use case that owns it — **and decides no rule
 * of its own**.
 *
 * Every branch here ends in a use case: a purchase in `CreateTransactionUseCase` (or
 * `AddInstallmentUseCase` for a plan), a transfer in `TransferBetweenAccountsUseCase`, a
 * settlement in `PayInvoicePaymentUseCase`, and the two adjustments in
 * `AdjustBalanceUseCase` and `AdjustInvoiceUseCase`. Nothing composes an intent for the
 * ledger's write boundary directly.
 *
 * **Which invoice a purchase falls in is not decided here.** The month is read off the
 * card's own cycle (`CreditCard.invoiceWindowOn` and `CreditCard.dueMonthFor`, the single
 * statement of that rule in the domain), and the bill itself is then obtained by
 * `GetOrCreateInvoiceForMonthUseCase` through `BuildTransactionUseCase` — the owner of
 * "get or create the invoice of this month". An item may also name the bill outright, and
 * then that is the month.
 *
 * **Identifiers are opaque.** A name, a label or free text never identifies an account, a
 * category, a card, an invoice or a budget: identifiers come from a previous read, and
 * text where one belongs is refused rather than matched.
 */
class TransactionItemResolver(
    private val accounts: IAccountRepository,
    private val creditCards: ICreditCardRepository,
    private val categories: ICategoryRepository,
    private val invoices: IInvoiceRepository,
    private val createTransaction: CreateTransactionUseCase,
    private val addInstallment: AddInstallmentUseCase,
    private val transferBetweenAccounts: TransferBetweenAccountsUseCase,
    private val payInvoicePayment: PayInvoicePaymentUseCase,
    private val adjustBalance: AdjustBalanceUseCase,
    private val adjustInvoice: AdjustInvoiceUseCase,
) {

    suspend fun resolve(item: JsonObject): Either<ToolError, ResolvedItem> {
        val reader = ItemReader(item)
        val intent = reader.intent() ?: return Either.Left(requireNotNull(reader.failure))
        val date = reader.date("date") ?: return Either.Left(requireNotNull(reader.failure))
        val title = reader.text("description")

        return when (intent) {
            WriteIntent.EXPENSE -> accountPosting(reader, date, title, TransactionType.EXPENSE)
            WriteIntent.INCOME -> accountPosting(reader, date, title, TransactionType.INCOME)
            WriteIntent.CARD_PURCHASE -> cardPurchase(reader, date, title)
            WriteIntent.TRANSFER -> transfer(reader, date)
            WriteIntent.INVOICE_PAYMENT -> invoicePayment(reader, date)
            WriteIntent.ACCOUNT_ADJUSTMENT -> accountAdjustment(reader, date)
            WriteIntent.INVOICE_ADJUSTMENT -> invoiceAdjustment(reader, date)
        }
    }

    // ---------------------------------------------------------------- postings

    private suspend fun accountPosting(
        reader: ItemReader,
        date: LocalDate,
        title: String?,
        type: TransactionType,
    ): Either<ToolError, ResolvedItem> {
        val accountId = reader.id("accountId") ?: return reader.refusal()
        val amount = reader.money("amount") ?: return reader.refusal()
        val categoryId = reader.optionalId("categoryId")
        reader.failure?.let { return Either.Left(it) }

        val account = account(accountId) ?: return notFound(WriteItemCodes.ACCOUNT_NOT_FOUND, "account", accountId)
        val category = categoryId?.let { id ->
            category(id) ?: return notFound(WriteItemCodes.CATEGORY_NOT_FOUND, "category", id)
        }
        category?.let { categoryTypeMismatch(it, type)?.let { error -> return Either.Left(error) } }
        currencyMismatch(amount, account.currency)?.let { return Either.Left(it) }

        val form = TransactionForm.from(
            type = type,
            amount = amount.asFormAmount(),
            title = title,
            date = dayMonthYear.format(date),
            category = category,
            target = TransactionTarget.ACCOUNT,
            creditCard = null,
            invoiceDueMonth = null,
            account = account,
        )

        return Either.Right(
            ResolvedItem(
                intent = if (type == TransactionType.EXPENSE) WriteIntent.EXPENSE else WriteIntent.INCOME,
                date = date,
                preview = buildJsonObject {
                    put("intent", (if (type == TransactionType.EXPENSE) WriteIntent.EXPENSE else WriteIntent.INCOME).name)
                    put("date", date.toString())
                    title?.let { put("description", it) }
                    putMoney("amount", amount)
                    putRef("account", account.id, account.name)
                    category?.let { putRef("category", it.id, it.name) }
                },
            ) {
                createTransaction(form).asToolResult { listOf(it) }
            },
        )
    }

    private suspend fun cardPurchase(
        reader: ItemReader,
        date: LocalDate,
        title: String?,
    ): Either<ToolError, ResolvedItem> {
        val creditCardId = reader.id("creditCardId") ?: return reader.refusal()
        val amount = reader.money("amount") ?: return reader.refusal()
        val categoryId = reader.optionalId("categoryId")
        val invoiceId = reader.optionalId("invoiceId")
        val installments = reader.optionalCount("installments") ?: 1
        reader.failure?.let { return Either.Left(it) }

        val card = creditCards.getCreditCardById(creditCardId)
            ?: return notFound(WriteItemCodes.CREDIT_CARD_NOT_FOUND, "credit card", creditCardId)
        val category = categoryId?.let { id ->
            category(id) ?: return notFound(WriteItemCodes.CATEGORY_NOT_FOUND, "category", id)
        }
        category?.let { categoryTypeMismatch(it, TransactionType.EXPENSE)?.let { error -> return Either.Left(error) } }
        val namedInvoice = invoiceId?.let { id ->
            invoices.getInvoiceById(id) ?: return notFound(WriteItemCodes.INVOICE_NOT_FOUND, "invoice", id)
        }
        card.currency?.let { currency -> currencyMismatch(amount, currency)?.let { return Either.Left(it) } }

        // Which bill a purchase falls in has one owner: the card's own cycle says which
        // window the date is in, and the same rule read the other way says which month
        // that cycle falls due. Getting or creating that bill is
        // `GetOrCreateInvoiceForMonthUseCase`'s, reached through the build.
        val dueMonth = namedInvoice?.dueMonth
            ?: card.dueMonthFor(card.invoiceWindowOn(date).closingMonth)

        val form = TransactionForm.from(
            type = TransactionType.EXPENSE,
            amount = amount.asFormAmount(),
            title = title,
            date = dayMonthYear.format(date),
            category = category,
            target = TransactionTarget.CREDIT_CARD,
            creditCard = card,
            invoiceDueMonth = dueMonth,
            account = null,
            installments = installments,
        )

        return Either.Right(
            ResolvedItem(
                intent = WriteIntent.CARD_PURCHASE,
                date = date,
                preview = buildJsonObject {
                    put("intent", WriteIntent.CARD_PURCHASE.name)
                    put("date", date.toString())
                    title?.let { put("description", it) }
                    putMoney("amount", amount)
                    putRef("creditCard", card.id, card.name)
                    category?.let { putRef("category", it.id, it.name) }
                    put("invoiceDueMonth", dueMonth.toString())
                    namedInvoice?.let { put("invoiceId", it.id) }
                    put("installments", installments)
                },
            ) {
                // A plan is one operation and never one call per payment: the use case
                // writes every payment as a single unit of work.
                if (installments > 1) {
                    addInstallment(form, installments).asToolResult { it }
                } else {
                    createTransaction(form).asToolResult { listOf(it) }
                }
            },
        )
    }

    private suspend fun transfer(reader: ItemReader, date: LocalDate): Either<ToolError, ResolvedItem> {
        val sourceId = reader.id("accountId") ?: return reader.refusal()
        val destinationId = reader.id("destinationAccountId") ?: return reader.refusal()
        val amount = reader.money("amount") ?: return reader.refusal()
        val destinationAmount = reader.optionalMoney("destinationAmount")
        reader.failure?.let { return Either.Left(it) }

        val source = account(sourceId) ?: return notFound(WriteItemCodes.ACCOUNT_NOT_FOUND, "account", sourceId)
        val destination = account(destinationId)
            ?: return notFound(WriteItemCodes.ACCOUNT_NOT_FOUND, "account", destinationId)

        currencyMismatch(amount, source.currency)?.let { return Either.Left(it) }
        destinationAmount?.let { arriving ->
            currencyMismatch(arriving, destination.currency)?.let { return Either.Left(it) }
        }

        val leaving = abs(amount.value)
        val arriving = destinationAmount?.let { abs(it.value) }

        return Either.Right(
            ResolvedItem(
                intent = WriteIntent.TRANSFER,
                date = date,
                preview = buildJsonObject {
                    put("intent", WriteIntent.TRANSFER.name)
                    put("date", date.toString())
                    putMoney("amount", amount)
                    destinationAmount?.let { putMoney("destinationAmount", it) }
                    putRef("account", source.id, source.name)
                    putRef("destinationAccount", destination.id, destination.name)
                },
            ) {
                // No rate is a parameter anywhere on this path: it is the quotient of the
                // two ends, derived and archived by the domain after the write.
                transferBetweenAccounts(
                    sourceAccountId = source.id,
                    destinationAccountId = destination.id,
                    amount = leaving,
                    date = date,
                    destinationAmount = arriving,
                ).asToolResult { listOf(it) }
            },
        )
    }

    private suspend fun invoicePayment(reader: ItemReader, date: LocalDate): Either<ToolError, ResolvedItem> {
        val invoiceId = reader.id("invoiceId") ?: return reader.refusal()
        val accountId = reader.id("accountId") ?: return reader.refusal()
        val paidAmount = reader.optionalMoney("amount")
        reader.failure?.let { return Either.Left(it) }

        val invoice = invoices.getInvoiceById(invoiceId)
            ?: return notFound(WriteItemCodes.INVOICE_NOT_FOUND, "invoice", invoiceId)
        val account = account(accountId) ?: return notFound(WriteItemCodes.ACCOUNT_NOT_FOUND, "account", accountId)
        paidAmount?.let { currencyMismatch(it, account.currency)?.let { error -> return Either.Left(error) } }

        return Either.Right(
            ResolvedItem(
                intent = WriteIntent.INVOICE_PAYMENT,
                date = date,
                preview = buildJsonObject {
                    put("intent", WriteIntent.INVOICE_PAYMENT.name)
                    put("date", date.toString())
                    putRef("invoice", invoice.id, invoice.dueMonth.toString())
                    putRef("account", account.id, account.name)
                    paidAmount?.let { putMoney("amount", it) }
                },
            ) {
                // The invoice's own side stays what it owes, in the card's currency; what
                // the call may state is what leaves the paying account when that account
                // is in another one.
                payInvoicePayment(
                    invoiceId = invoice.id,
                    date = date,
                    account = account,
                    paidAmount = paidAmount?.let { abs(it.value) },
                ).asToolResult { emptyList() }
            },
        )
    }

    private suspend fun accountAdjustment(reader: ItemReader, date: LocalDate): Either<ToolError, ResolvedItem> {
        val accountId = reader.id("accountId") ?: return reader.refusal()
        val target = reader.money("targetBalance") ?: return reader.refusal()
        reader.failure?.let { return Either.Left(it) }

        val account = account(accountId) ?: return notFound(WriteItemCodes.ACCOUNT_NOT_FOUND, "account", accountId)
        currencyMismatch(target, account.currency)?.let { return Either.Left(it) }

        return Either.Right(
            ResolvedItem(
                intent = WriteIntent.ACCOUNT_ADJUSTMENT,
                date = date,
                preview = buildJsonObject {
                    put("intent", WriteIntent.ACCOUNT_ADJUSTMENT.name)
                    put("date", date.toString())
                    putRef("account", account.id, account.name)
                    putMoney("targetBalance", target)
                },
            ) {
                adjustBalance(
                    targetBalance = target.value,
                    adjustmentDate = date,
                    account = account,
                ).asToolResult { emptyList() }
            },
        )
    }

    private suspend fun invoiceAdjustment(reader: ItemReader, date: LocalDate): Either<ToolError, ResolvedItem> {
        val invoiceId = reader.id("invoiceId") ?: return reader.refusal()
        val target = reader.money("targetBalance") ?: return reader.refusal()
        reader.failure?.let { return Either.Left(it) }

        val invoice = invoices.getInvoiceById(invoiceId)
            ?: return notFound(WriteItemCodes.INVOICE_NOT_FOUND, "invoice", invoiceId)
        invoice.creditCard.currency?.let { currency ->
            currencyMismatch(target, currency)?.let { return Either.Left(it) }
        }

        return Either.Right(
            ResolvedItem(
                intent = WriteIntent.INVOICE_ADJUSTMENT,
                date = date,
                preview = buildJsonObject {
                    put("intent", WriteIntent.INVOICE_ADJUSTMENT.name)
                    put("date", date.toString())
                    putRef("invoice", invoice.id, invoice.dueMonth.toString())
                    putMoney("targetBalance", target)
                },
            ) {
                // Adjusting an invoice and adjusting an account are the same mechanism;
                // what distinguishes them is only where the dimension lands.
                adjustInvoice(
                    invoice = invoice,
                    target = target.value,
                    adjustmentDate = date,
                ).asToolResult { emptyList() }
            },
        )
    }

    // ---------------------------------------------------------------- lookups

    /**
     * The account an identifier names — **only ever one of the user's own**.
     *
     * Resolved through the account facade rather than through the chart, and that is the
     * point: the chart also holds the rows the ledger's write boundary creates for itself
     * — the two nominals, reconciliation and conversion — and those are mechanism. They
     * appear in no listing of this surface, so they must not be reachable as a parameter
     * of it either; an identifier naming one is an identifier naming nothing here.
     *
     * Closed accounts *are* included: a write to one is refused by the write boundary,
     * which owns that rule, and answering "no such account" instead would be telling the
     * user something false.
     */
    private suspend fun account(id: Long): Account? =
        accounts.getAllAccountsIncludingClosed().firstOrNull { it.id == id }

    private suspend fun category(id: Long): Category? = categories.getCategoryById(id)

    private fun notFound(code: String, what: String, id: Long): Either<ToolError, Nothing> = Either.Left(
        ToolError.invalidInput(
            code = code,
            message = "No $what with id $id. Identifiers come from a read of this server, and " +
                "nothing is created implicitly to satisfy one.",
        ),
    )

    private fun categoryTypeMismatch(category: Category, type: TransactionType): ToolError? =
        categoryTypeRefusal(category, type)

    private fun currencyMismatch(amount: ItemMoney, declared: String): ToolError? =
        if (amount.currency == declared) {
            null
        } else {
            ToolError.invalidInput(
                code = WriteItemCodes.CURRENCY_MISMATCH,
                message = "The amount is in ${amount.currency} and the account it posts to declares " +
                    "$declared. An account's currency is fixed at creation; state the amount in it.",
            )
        }
}

/**
 * The refusal a category that does not classify [type] earns, or `null` when it does.
 *
 * Refused rather than dropped, wherever a category is chosen: [TransactionForm] discards a
 * category the direction does not accept, and letting that happen would record the item
 * unclassified while the call looked like it succeeded. `Category.Type.isAccept` is the
 * domain's rule; this is only the refusal that states it once for the whole write surface.
 */
internal fun categoryTypeRefusal(category: Category, type: TransactionType): ToolError? =
    if (category.type.isAccept(type)) {
        null
    } else {
        ToolError.invalidInput(
            code = WriteItemCodes.CATEGORY_TYPE_MISMATCH,
            message = "Category ${category.id} is declared ${category.type.name} and does not classify " +
                "a ${type.name}. Pass a category of the matching type, or none.",
        )
    }

/** A money value as an item states it: the currency, and the integer minor unit. */
data class ItemMoney(val currency: String, val minorUnits: Long) {

    /** The same amount in the major unit — the form every use case takes. */
    val value: Double get() = minorUnits / 100.0

    /**
     * The amount as a [TransactionForm] holds one: the digits of the minor unit.
     *
     * The form carries text because it is filled in by a keyboard, and `moneyToDouble`
     * reads it as cents. Handing it a formatted number would be handing it a locale.
     */
    fun asFormAmount(): String = abs(minorUnits).toString()
}

/**
 * Reads one item, accumulating the first refusal rather than throwing — the same shape the
 * tools' argument reader has, and for the same reason.
 */
internal class ItemReader(private val item: JsonObject) {

    var failure: ToolError? = null
        private set

    fun <T> refusal(): Either<ToolError, T> = Either.Left(
        failure ?: ToolError.invalidInput(WriteItemCodes.INVALID_FIELD, "The item could not be read"),
    )

    private fun refuse(error: ToolError): Nothing? {
        if (failure == null) failure = error
        return null
    }

    private fun primitive(key: String): JsonPrimitive? =
        (item[key] as? JsonPrimitive)?.takeIf { it != JsonNull }

    fun intent(): WriteIntent? {
        val raw = primitive("intent")?.content ?: return refuse(
            ToolError.invalidInput(
                code = WriteItemCodes.UNKNOWN_INTENT,
                message = "`intent` is required, and is one of ${WriteIntent.entries.joinToString { it.name }}",
            ),
        )
        return WriteIntent.entries.firstOrNull { it.name == raw } ?: refuse(
            ToolError.invalidInput(
                code = WriteItemCodes.UNKNOWN_INTENT,
                message = "`$raw` is not an intent this server writes. " +
                    "One of ${WriteIntent.entries.joinToString { it.name }}.",
            ),
        )
    }

    fun date(key: String): LocalDate? {
        val raw = primitive(key)?.content ?: return refuse(
            ToolError.invalidInput(WriteItemCodes.MISSING_FIELD, "`$key` is required, as YYYY-MM-DD"),
        )
        return when (val parsed = parseCivilDate(raw)) {
            is CivilDate.Accepted -> parsed.date
            is CivilDate.Refused -> refuse(parsed.error)
        }
    }

    fun text(key: String): String? = primitive(key)?.content?.takeIf { it.isNotBlank() }

    /**
     * An identifier, which is **always** an integer.
     *
     * Text here is refused rather than matched against a name. A name is not a key on this
     * surface: two accounts may share one, a rename would silently repoint a write, and an
     * agent guessing a name would write to whatever it happened to hit.
     */
    fun id(key: String): Long? {
        val raw = primitive(key) ?: return refuse(
            ToolError.invalidInput(WriteItemCodes.MISSING_FIELD, "`$key` is required, and is an identifier"),
        )
        return raw.content.toLongOrNull() ?: refuse(
            ToolError.invalidInput(
                code = WriteItemCodes.NAME_IS_NOT_AN_IDENTIFIER,
                message = "`$key` must be the identifier a read of this server returned, not a name: " +
                    "received `${raw.content}`",
            ),
        )
    }

    fun optionalId(key: String): Long? = if (item[key] == null || item[key] == JsonNull) null else id(key)

    fun optionalCount(key: String): Int? {
        val raw = primitive(key)?.content ?: return null
        val value = raw.toIntOrNull()
        return when {
            value == null || value < 1 -> refuse(
                ToolError.invalidInput(WriteItemCodes.INVALID_FIELD, "`$key` must be a positive whole number; received `$raw`"),
            )

            else -> value
        }
    }

    fun money(key: String): ItemMoney? {
        val value = item[key] as? JsonObject ?: return refuse(
            ToolError.invalidInput(
                code = WriteItemCodes.MISSING_FIELD,
                message = "`$key` is required, as { currency, minorUnits }: money crosses this boundary " +
                    "with its currency and an integer in the minor unit, never as a bare number.",
            ),
        )
        return readMoney(key, value)
    }

    fun optionalMoney(key: String): ItemMoney? =
        if (item[key] == null || item[key] == JsonNull) null else money(key)

    private fun readMoney(key: String, value: JsonObject): ItemMoney? {
        val currency = (value["currency"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return refuse(
            ToolError.invalidInput(WriteItemCodes.MISSING_FIELD, "`$key.currency` is required"),
        )
        val minorUnits = (value["minorUnits"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return refuse(
            ToolError.invalidInput(
                code = WriteItemCodes.INVALID_FIELD,
                message = "`$key.minorUnits` must be an integer in the minor unit — cents. " +
                    "A decimal would be a double to most consumers, and a cent that never existed.",
            ),
        )
        val scale = (value["scale"] as? JsonPrimitive)?.content?.toIntOrNull()
        if (scale != null && scale != MONEY_SCALE) {
            return refuse(
                ToolError.invalidInput(
                    code = WriteItemCodes.INVALID_FIELD,
                    message = "`$key.scale` is $scale; this app holds money at scale $MONEY_SCALE only.",
                ),
            )
        }
        return ItemMoney(currency, minorUnits)
    }
}

/** One money value, as this surface writes one back into a preview. */
internal fun JsonObjectBuilder.putMoney(name: String, amount: ItemMoney) = putJsonObject(name) {
    put("currency", amount.currency)
    put("minorUnits", amount.minorUnits)
    put("scale", MONEY_SCALE)
}

/** The identifier and the name of a facade, so a preview never states one without the other. */
internal fun JsonObjectBuilder.putRef(name: String, id: Long, label: String) = putJsonObject(name) {
    put("id", id)
    put("name", label)
}

/**
 * The domain's own refusal, as the agent receives it: the family it belongs to and the
 * English message the domain wrote for a log.
 *
 * **No `UiText` crosses here.** The agent is a consumer of logs and not of screens, and a
 * tool answering the user's language to an English client would be leaking the presentation
 * layer through a boundary that is not one.
 */
internal fun Throwable.asToolError(): ToolError = when (this) {
    is BuildTransactionException -> ToolError.domainRule(DomainRefusalCodes.BUILD_TRANSACTION, error.message)
    is TransferException -> ToolError.domainRule(DomainRefusalCodes.TRANSFER, error.message)
    is InvoiceException -> ToolError.domainRule(DomainRefusalCodes.INVOICE, error.message)
    is ClosedAccountException -> ToolError.domainRule(DomainRefusalCodes.LEDGER, error.message)
    is UnbalancedTransactionException -> ToolError.domainRule(DomainRefusalCodes.LEDGER, error.message)
    is AccountNotAdjustedException -> ToolError.domainRule(DomainRefusalCodes.NOTHING_TO_ADJUST, messageOrType())
    is InvoiceNotAdjustedException -> ToolError.domainRule(DomainRefusalCodes.NOTHING_TO_ADJUST, messageOrType())
    else -> ToolError.domainRule(DomainRefusalCodes.OTHER, messageOrType())
}

private fun Throwable.messageOrType(): String = message?.takeIf { it.isNotBlank() } ?: "The domain refused the operation"

/** The domain's `Either` in the vocabulary of this surface. */
internal fun <T> Either<Throwable, T>.asToolResult(
    written: (T) -> List<Transaction>,
): Either<ToolError, List<Transaction>> = fold(
    ifLeft = { Either.Left(it.asToolError()) },
    ifRight = { Either.Right(written(it)) },
)
