package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import kotlinx.datetime.YearMonth

/**
 * One month of a real user's life, seeded into a real ledger: a salary, a grocery run paid from the
 * account, another put on the card, a transfer between the user's own accounts, and the payment of
 * the card's invoice.
 *
 * The last two are the point. They move money and are **not spending**, and the ledger already knows
 * it — which is what the questions family is held against.
 */
internal object MarchWorld {

    val MONTH = YearMonth(2026, 3)

    const val ACCOUNT_CHECKING = 1L
    const val ACCOUNT_SAVINGS = 2L
    const val ACCOUNT_CARD = 10L

    const val NOMINAL_EXPENSE = 100L
    const val NOMINAL_INCOME = 200L
    const val EQUITY = 300L

    const val DIMENSION_GROCERIES = 1L
    const val DIMENSION_SALARY = 2L
    const val DIMENSION_INVOICE = 10L

    const val CARD_LIMIT = 5_000.0

    /** What each figure of March comes to, in the major unit — computed here once, asserted often. */
    const val SALARY = 5_000.00
    const val GROCERIES_FROM_ACCOUNT = 300.00
    const val GROCERIES_ON_CARD = 200.00
    const val SPENT = GROCERIES_FROM_ACCOUNT + GROCERIES_ON_CARD
    const val TRANSFERRED = 1_000.00
    const val INVOICE_PAID = 150.00

    /** Accounts only: salary less what left them, the transfer netting to nothing. */
    const val ACCOUNT_BALANCE = SALARY - GROCERIES_FROM_ACCOUNT - INVOICE_PAID

    /** The same money with the card's remaining debt taken off. */
    const val CARD_OWED = GROCERIES_ON_CARD - INVOICE_PAID
    const val NET_WORTH = ACCOUNT_BALANCE - CARD_OWED
}

internal suspend fun AgentWorld.seedMarch(): CreditCard {
    account(MarchWorld.ACCOUNT_CHECKING, "Nubank", isDefault = true)
    account(MarchWorld.ACCOUNT_SAVINGS, "Poupança")
    val card = card(
        id = 1,
        accountId = MarchWorld.ACCOUNT_CARD,
        name = "Cartão",
        limit = MarchWorld.CARD_LIMIT,
    )

    ledgerAccount(MarchWorld.NOMINAL_EXPENSE, AccountEntity.Type.EXPENSE, "Despesas")
    ledgerAccount(MarchWorld.NOMINAL_INCOME, AccountEntity.Type.INCOME, "Receitas")
    ledgerAccount(MarchWorld.EQUITY, AccountEntity.Type.EQUITY, "Reconciliação")

    category(id = 1, dimensionId = MarchWorld.DIMENSION_GROCERIES, name = "Mercado")
    category(
        id = 2,
        dimensionId = MarchWorld.DIMENSION_SALARY,
        name = "Salário",
        type = Category.Type.INCOME,
    )
    invoice(
        id = 1,
        dimensionId = MarchWorld.DIMENSION_INVOICE,
        card = card,
        month = MarchWorld.MONTH,
        status = Invoice.Status.OPEN,
    )

    // The salary.
    posting(
        "2026-03-05",
        MarchWorld.ACCOUNT_CHECKING posts 500_000,
        (MarchWorld.NOMINAL_INCOME posts -500_000).taggedWith(MarchWorld.DIMENSION_SALARY),
    )
    // Groceries, paid straight from the account.
    posting(
        "2026-03-07",
        MarchWorld.ACCOUNT_CHECKING posts -30_000,
        (MarchWorld.NOMINAL_EXPENSE posts 30_000).taggedWith(MarchWorld.DIMENSION_GROCERIES),
    )
    // Groceries again, this time on the card: no account leg at all.
    posting(
        "2026-03-09",
        (MarchWorld.ACCOUNT_CARD posts -20_000).taggedWith(MarchWorld.DIMENSION_INVOICE),
        (MarchWorld.NOMINAL_EXPENSE posts 20_000).taggedWith(MarchWorld.DIMENSION_GROCERIES),
    )
    // A transfer between the user's own accounts: both legs inside the perimeter.
    posting(
        "2026-03-11",
        MarchWorld.ACCOUNT_CHECKING posts -100_000,
        MarchWorld.ACCOUNT_SAVINGS posts 100_000,
    )
    // Paying part of the card's invoice: it settles a debt, it does not spend.
    posting(
        "2026-03-13",
        MarchWorld.ACCOUNT_CHECKING posts -15_000,
        (MarchWorld.ACCOUNT_CARD posts 15_000).taggedWith(MarchWorld.DIMENSION_INVOICE),
    )

    return card
}
