package com.neoutils.finsight.domain.model

/**
 * Well-known names of the accounts the app creates for itself rather than for the
 * user: the `EQUITY` counterpart of every adjustment and write-off, the fallback
 * buckets for uncategorized money, and the stand-ins for accounts deleted before
 * closure existed. Mirrored by the ledger migration SQL in `:core:database`; keep
 * both in sync.
 */
object SystemAccount {
    const val RECONCILIATION = "Reconciliação"
    const val UNCATEGORIZED_EXPENSE = "Sem categoria (despesa)"
    const val UNCATEGORIZED_INCOME = "Sem categoria (receita)"

    /**
     * The reconstructed home of legs whose account or card was deleted back when
     * the app removed them instead of closing them. One per type: the real type
     * survives in the legacy `target`, the name and the multiplicity do not.
     */
    const val CLOSED_ACCOUNT = "Conta encerrada"
    const val CLOSED_CARD = "Cartão encerrado"
}
