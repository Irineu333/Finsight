package com.neoutils.finsight.domain.model

/**
 * The user-facing label of an [Operation], derived from the [AccountType]s of the
 * accounts its entries reference — never persisted as independent state.
 *
 * `ASSET`→`EXPENSE` is [EXPENSE]; `INCOME`→`ASSET` is [INCOME]; `ASSET`→`ASSET` is
 * [TRANSFER]; `ASSET`→`LIABILITY` is [PAYMENT].
 */
enum class OperationLabel {
    EXPENSE,
    INCOME,
    TRANSFER,
    PAYMENT,
}
