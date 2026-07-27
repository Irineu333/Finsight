package com.neoutils.finsight.domain.model

import kotlinx.serialization.Serializable

/**
 * The user-facing label of an [Transaction], derived from the [AccountType]s of the
 * accounts its entries reference — never persisted as independent state.
 *
 * An `EQUITY` counter-leg makes it an [ADJUSTMENT] regardless of where the money
 * sits; otherwise `ASSET`→`EXPENSE` is [EXPENSE]; `INCOME`→`ASSET` is [INCOME];
 * `ASSET`→`LIABILITY` is [PAYMENT]; `ASSET`→`ASSET` is [TRANSFER]. The set is a
 * total function over the seven ledger forms.
 *
 * `@Serializable` because it is a type-safe navigation argument (`TransactionsRoute`):
 * resolving the route's `typeMap` calls `serializer(kType)`, and on Kotlin/Native that
 * only succeeds for an enum with a generated serializer.
 */
@Serializable
enum class TransactionLabel {
    EXPENSE,
    INCOME,
    ADJUSTMENT,
    TRANSFER,
    PAYMENT,
}
