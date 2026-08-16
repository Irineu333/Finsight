@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

/**
 * The world the operations family is exercised against.
 *
 * It differs from `RegistrationWorld` in what it has to contain rather than in how it works: an
 * operation moves something that already exists, so the fixture has to hold a bill that can be
 * paid, a cycle that can be closed, a template with a cycle still pending, and an account in a
 * second currency for the crossing. Everything is delegated to the [AgentWorld] it wraps, so an
 * assertion reaches the **real** ledger and the same facade stores the tools write through.
 */
internal data class OperationsWorld(
    val world: AgentWorld,
    /** BRL, the default, holding money to pay with. */
    val checkingId: Long,
    /** BRL, the other end of a same-currency transfer. */
    val savingsId: Long,
    /** USD, the other end of the crossing. */
    val dollarsId: Long,
    /** The February cycle: closed, and owing what a purchase put on it. */
    val closedInvoiceId: Long,
    /** The March cycle: open, and owing what a purchase put on it. */
    val openInvoiceId: Long,
    /** The April cycle: declared, never opened, owing nothing. */
    val futureInvoiceId: Long,
    /** The template with a cycle pending, carrying both a title and a category. */
    val recurringId: Long,
    val categoryId: Long,
    val cardId: Long,
    val cardAccountId: Long,
    val harness: McpServerHarness? = null,
) {
    val database get() = world.database
    val entryRepository get() = world.entryRepository
    val transactionRepository get() = world.transactionRepository
    val accountRepository get() = world.accountRepository
    val categoryRepository get() = world.categoryRepository
    val creditCardRepository get() = world.creditCardRepository
    val invoiceRepository get() = world.invoiceRepository
    val recurringRepository get() = world.recurringRepository
    val occurrences get() = world.occurrences
    val exchangeRates get() = world.exchangeRates

    /** What the agent left behind, from the log the server it is talking to actually writes to. */
    suspend fun activityOf(client: McpConversation): List<AgentActivity> {
        check(client.sessionId != null) { "the conversation never opened" }
        return requireNotNull(harness) { "no server was started for this world" }
            .activity.observeAll().first()
    }
}

private const val ACCOUNT_CHECKING = 1L
private const val ACCOUNT_SAVINGS = 2L
private const val ACCOUNT_DOLLARS = 3L
private const val ACCOUNT_CARD = 10L
private const val NOMINAL_EXPENSE = 100L
private const val NOMINAL_INCOME = 200L
private const val DIMENSION_SUBSCRIPTIONS = 1L
private const val DIMENSION_OPEN_INVOICE = 10L
private const val DIMENSION_CLOSED_INVOICE = 20L
private const val DIMENSION_FUTURE_INVOICE = 30L
private const val USD = "USD"

/** What the figures of this world come to, in the major unit — stated once, asserted often. */
internal object Operations {
    const val BALANCE_BEFORE = 1_000.00
    const val CLOSED_INVOICE_OWED = 300.00
    const val OPEN_INVOICE_OWED = 200.00

    /** What the paying account holds once the closed cycle has been settled in full. */
    const val BALANCE_AFTER_PAYING = BALANCE_BEFORE - CLOSED_INVOICE_OWED

    /** The template's cycle, in the major unit. */
    const val RECURRING_AMOUNT = 39.90
    const val RECURRING_TITLE = "Netflix"
    const val CATEGORY_NAME = "Assinaturas"
}

private const val BALANCE_BEFORE_CENTS = 100_000L

/**
 * A user with three accounts, a card with two cycles, and a template waiting to be confirmed.
 *
 * The dates are chosen so the operations are legal on the day the world lives in (15 March 2026):
 * February's cycle closed on the 20th and falls due on the 28th, both behind that day, so it can be
 * paid; March's is still open and closes on the 20th, so it can be closed and paid down.
 */
internal suspend fun AgentWorld.seedOperations(): OperationsWorld {
    account(ACCOUNT_CHECKING, "Nubank", isDefault = true)
    account(ACCOUNT_SAVINGS, "Poupança")
    account(ACCOUNT_DOLLARS, "Conta USD", currency = USD)
    val card = card(id = 1, accountId = ACCOUNT_CARD, name = "Cartão", limit = 5_000.0)

    ledgerAccount(NOMINAL_EXPENSE, AccountEntity.Type.EXPENSE, "Despesas")
    ledgerAccount(NOMINAL_INCOME, AccountEntity.Type.INCOME, "Receitas")

    val category = category(
        id = 1,
        dimensionId = DIMENSION_SUBSCRIPTIONS,
        name = Operations.CATEGORY_NAME,
        type = Category.Type.EXPENSE,
    )

    val closed = invoice(
        id = 1,
        dimensionId = DIMENSION_CLOSED_INVOICE,
        card = card,
        month = YearMonth(2026, 2),
        status = Invoice.Status.CLOSED,
    )
    val open = invoice(
        id = 2,
        dimensionId = DIMENSION_OPEN_INVOICE,
        card = card,
        month = YearMonth(2026, 3),
        status = Invoice.Status.OPEN,
    )
    // Declared and not yet on the air. It is what `open_invoice` promotes rather than duplicating,
    // and what closing March's cycle hands the card next.
    val future = invoice(
        id = 3,
        dimensionId = DIMENSION_FUTURE_INVOICE,
        card = card,
        month = YearMonth(2026, 4),
        status = Invoice.Status.FUTURE,
    )

    // What the account holds before anything is paid out of it — the figure 11.2 watches move.
    posting(
        "2026-01-05",
        ACCOUNT_CHECKING posts BALANCE_BEFORE_CENTS,
        NOMINAL_INCOME posts -BALANCE_BEFORE_CENTS,
        title = "Salário",
    )
    // A purchase in each cycle, so both owe something: an invoice owing nothing cannot be paid,
    // and one owing nothing is settled by closing rather than by a payment. The card's leg is a
    // credit — negative, the ledger's own sign for a liability taken on.
    posting(
        "2026-02-10",
        (ACCOUNT_CARD posts -30_000).taggedWith(DIMENSION_CLOSED_INVOICE),
        (NOMINAL_EXPENSE posts 30_000).taggedWith(DIMENSION_SUBSCRIPTIONS),
        title = "Compra de fevereiro",
    )
    posting(
        "2026-03-05",
        (ACCOUNT_CARD posts -20_000).taggedWith(DIMENSION_OPEN_INVOICE),
        (NOMINAL_EXPENSE posts 20_000).taggedWith(DIMENSION_SUBSCRIPTIONS),
        title = "Compra de março",
    )

    val recurring = Recurring(
        id = 1,
        type = TransactionType.EXPENSE,
        amount = Operations.RECURRING_AMOUNT,
        title = Operations.RECURRING_TITLE,
        dayOfMonth = 10,
        category = category,
        account = accounts.first { it.id == ACCOUNT_CHECKING },
        creditCard = null,
        createdAt = LocalDate(2026, 1, 1)
            .atTime(0, 0)
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds(),
    ).also { recurringList += it }

    return OperationsWorld(
        world = this,
        checkingId = ACCOUNT_CHECKING,
        savingsId = ACCOUNT_SAVINGS,
        dollarsId = ACCOUNT_DOLLARS,
        closedInvoiceId = closed.id,
        openInvoiceId = open.id,
        futureInvoiceId = future.id,
        recurringId = recurring.id,
        categoryId = category.id,
        cardId = card.id,
        cardAccountId = ACCOUNT_CARD,
    )
}
