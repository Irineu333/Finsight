package com.neoutils.finsight.domain.model

/**
 * The kind of a ledger dimension — the analytic axis a leg can be tagged with,
 * beyond the account it posts to.
 *
 * The ledger does not know what an invoice or a category *is*. What it knows is
 * carried entirely by [landsOn]: the account natures on which a dimension of this
 * kind may land. `INVOICE` here is a label legible to a human reading the schema,
 * not a concept the ledger manipulates — no query joins a facade table and no
 * aggregation branches on the kind. The same precedent already exists one level
 * down, where the SQL writes the literal `'ASSET'` without the DAO knowing what an
 * asset is.
 *
 * A plain string was rejected: the silent typo — a sum by the wrong dimension, with
 * no error — is the exact class of defect the kind exists to kill, and a string
 * would preserve only the textual opacity, which was never the target.
 */
enum class DimensionKind(val landsOn: Set<AccountType>) {
    INVOICE(setOf(AccountType.LIABILITY)),
    CATEGORY(setOf(AccountType.INCOME, AccountType.EXPENSE)),
}
