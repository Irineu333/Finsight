package com.neoutils.finsight.domain.model

/**
 * Well-known names of the accounts the app creates for itself rather than for the
 * user: the `EQUITY` counterpart of every adjustment and write-off, the two nominal
 * accounts every income and expense lands on, and the stand-ins for accounts deleted
 * before closure existed. Mirrored by the ledger migration SQL in `:core:database`;
 * keep both in sync.
 *
 * None of these is ever rendered. They are lookup keys, and the accounts they name
 * are invisible by construction: every listing and selector filters `type = 'ASSET'`,
 * which no row here is (design D10). What the user reads on a nominal leg is the
 * name of the *category* its dimension points at — or, with no dimension, the
 * "uncategorized" string resource.
 */
object SystemAccount {
    const val RECONCILIATION = "Reconciliação"

    /**
     * The two nominal accounts of the whole chart: every expense lands on one and
     * every income on the other, told apart by the dimension of the category the
     * leg carries. They replace the per-category accounts — a category is a
     * dimension now, not a row in the chart of accounts (design D4).
     */
    const val EXPENSES = "Despesas"
    const val INCOMES = "Receitas"

    /**
     * The reconstructed home of legs whose account or card was deleted back when
     * the app removed them instead of closing them. One per type: the real type
     * survives in the legacy `target`, the name and the multiplicity do not.
     */
    const val CLOSED_ACCOUNT = "Conta encerrada"
    const val CLOSED_CARD = "Cartão encerrado"
}
