package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import kotlinx.coroutines.flow.first
import kotlinx.datetime.YearMonth

/**
 * The world the registration family is exercised against, and the identities a test needs to name.
 *
 * It delegates every read to the [AgentWorld] it wraps, so an assertion reaches the **real** ledger
 * and the same facade stores the tools write through. What it adds is the three postings whose shape
 * the family turns on — an ordinary expense, a transfer and a card payment — because "editing a
 * transfer is refused" is only a statement about the app if the transfer is a real two-legged
 * posting in a real ledger.
 */
internal data class RegistrationWorld(
    val world: AgentWorld,
    /** An ordinary expense: one monetary leg, so the rewrite can express it. */
    val groceriesId: Long,
    /** Two monetary legs, both of them the user's own accounts. */
    val transferId: Long,
    /** Two monetary legs, one of them the card's. */
    val paymentId: Long,
    val harness: McpServerHarness? = null,
) {
    val cards get() = world.cards
    val invoices get() = world.invoices
    val database get() = world.database
    val transactionRepository get() = world.transactionRepository
    val accountRepository get() = world.accountRepository
    val categoryRepository get() = world.categoryRepository
    val creditCardRepository get() = world.creditCardRepository
    val invoiceRepository get() = world.invoiceRepository
    val budgetRepository get() = world.budgetRepository
    val recurringRepository get() = world.recurringRepository
    val installmentRepository get() = world.installmentRepository

    /** What the agent left behind, from the log the server it is talking to actually writes to. */
    suspend fun activityOf(client: McpConversation): List<AgentActivity> {
        check(client.sessionId != null) { "the conversation never opened" }
        return requireNotNull(harness) { "no server was started for this world" }
            .activity.observeAll().first()
    }
}

private const val ACCOUNT_CHECKING = 1L
private const val ACCOUNT_SAVINGS = 2L
private const val ACCOUNT_CARD = 10L
private const val NOMINAL_EXPENSE = 100L
private const val NOMINAL_INCOME = 200L
private const val DIMENSION_GROCERIES = 1L
private const val DIMENSION_INVOICE = 10L

/**
 * A user with two accounts, a card with an open invoice, one category, and the three postings the
 * family's refusals turn on.
 *
 * Written out rather than reusing `seedMarch` because the titles matter here: an edit that keeps
 * what the call did not name can only be checked against a posting that had a title to keep.
 */
internal suspend fun AgentWorld.seedRegistration(): RegistrationWorld {
    account(ACCOUNT_CHECKING, "Nubank", isDefault = true)
    account(ACCOUNT_SAVINGS, "Poupança")
    val card = card(id = 1, accountId = ACCOUNT_CARD, name = "Cartão", limit = 5_000.0)

    ledgerAccount(NOMINAL_EXPENSE, AccountEntity.Type.EXPENSE, "Despesas")
    ledgerAccount(NOMINAL_INCOME, AccountEntity.Type.INCOME, "Receitas")

    category(id = 1, dimensionId = DIMENSION_GROCERIES, name = "Mercado", type = Category.Type.EXPENSE)
    invoice(
        id = 1,
        dimensionId = DIMENSION_INVOICE,
        card = card,
        month = YearMonth(2026, 3),
        status = Invoice.Status.OPEN,
    )

    val groceries = posting(
        "2026-03-07",
        ACCOUNT_CHECKING posts -30_000,
        (NOMINAL_EXPENSE posts 30_000).taggedWith(DIMENSION_GROCERIES),
        title = "Mercado",
    )
    val transfer = posting(
        "2026-03-11",
        ACCOUNT_CHECKING posts -100_000,
        ACCOUNT_SAVINGS posts 100_000,
        title = "Reserva",
    )
    val payment = posting(
        "2026-03-13",
        ACCOUNT_CHECKING posts -15_000,
        (ACCOUNT_CARD posts 15_000).taggedWith(DIMENSION_INVOICE),
        title = "Pagamento da fatura",
    )

    return RegistrationWorld(
        world = this,
        groceriesId = groceries,
        transferId = transfer,
        paymentId = payment,
    )
}
