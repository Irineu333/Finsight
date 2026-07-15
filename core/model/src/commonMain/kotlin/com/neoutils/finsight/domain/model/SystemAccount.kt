package com.neoutils.finsight.domain.model

/**
 * Well-known names of the `EQUITY` and fallback system accounts seeded by the
 * ledger migration. Used as the contra-account for adjustments and for legacy
 * transactions with no category. Mirrored by the `MIGRATION_7_8` SQL in
 * `:core:database`; keep both in sync.
 */
object SystemAccount {
    const val RECONCILIATION = "Reconciliação"
    const val INITIAL_BALANCE = "Saldo Inicial"
    const val UNCATEGORIZED_EXPENSE = "Sem categoria (despesa)"
    const val UNCATEGORIZED_INCOME = "Sem categoria (receita)"
}
