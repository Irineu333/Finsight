package com.neoutils.finsight.domain.model

/**
 * The user-facing label of an [Transaction], derived from the [AccountType]s of the
 * accounts its entries reference — never persisted as independent state.
 *
 * An `EQUITY` counter-leg makes it an [ADJUSTMENT] regardless of where the money
 * sits; otherwise `ASSET`→`EXPENSE` is [EXPENSE]; `INCOME`→`ASSET` is [INCOME];
 * `ASSET`→`LIABILITY` is [PAYMENT]; `ASSET`→`ASSET` is [TRANSFER]. The set is a
 * total function over the seven ledger forms.
 */
enum class TransactionLabel {
    EXPENSE,
    INCOME,
    ADJUSTMENT,
    TRANSFER,
    PAYMENT,
}
