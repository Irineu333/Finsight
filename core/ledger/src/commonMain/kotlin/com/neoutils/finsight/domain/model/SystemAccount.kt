package com.neoutils.finsight.domain.model

/**
 * The accounts the app creates for itself rather than for the user: the `EQUITY`
 * counterpart of every adjustment and write-off, the `CONVERSION` row that absorbs an
 * exchange residue, the two nominal accounts every income and expense lands on, and the
 * stand-ins for accounts deleted before closure existed. Mirrored by the ledger migration
 * SQL in `:core:database`; keep both in sync.
 *
 * None of these is ever rendered. [accountName] is a lookup key, and the accounts it names
 * are invisible by construction: every listing and selector filters `type = 'ASSET'`,
 * which no row here is (design D10). What the user reads on a nominal leg is the name of
 * the *category* its dimension points at — or, with no dimension, the "uncategorized"
 * string resource.
 *
 * This is an enum rather than a bag of names because [nature] stopped being enough to
 * identify one: `EQUITY` now has **two** system accounts per currency — reconciliation and
 * conversion — so what identifies a row of the chart is the triple *(type, name,
 * currency)*, and the pair *(this, currency)* is the key a caller states.
 */
enum class SystemAccount(val accountName: String, val nature: AccountType) {

    RECONCILIATION("Reconciliação", AccountType.EQUITY),

    /**
     * Where the residue of a transaction that crosses currencies lands — one row per
     * currency, so `currency` means the same thing in every line of the chart.
     *
     * Created by the write boundary alone, while completing a cross-currency operation,
     * and named by no intent: an account nobody can post to by hand.
     */
    CONVERSION("Conversão", AccountType.CONVERSION),

    /**
     * The two nominal accounts of the whole chart, one pair per currency: every expense
     * lands on one and every income on the other, told apart by the dimension of the
     * category the leg carries. They replace the per-category accounts — a category is a
     * dimension now, not a row in the chart of accounts (design D4).
     */
    EXPENSES("Despesas", AccountType.EXPENSE),
    INCOMES("Receitas", AccountType.INCOME),

    /**
     * The reconstructed home of legs whose account or card was deleted back when the app
     * removed them instead of closing them. One per type: the real type survives in the
     * legacy `target`, the name and the multiplicity do not. Artifacts of the `v7 → v9`
     * migration, they exist only in the legacy currency and are never created at runtime.
     */
    CLOSED_ACCOUNT("Conta encerrada", AccountType.ASSET),
    CLOSED_CARD("Cartão encerrado", AccountType.LIABILITY),
}
