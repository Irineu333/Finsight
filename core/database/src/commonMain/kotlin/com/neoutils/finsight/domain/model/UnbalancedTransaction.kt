package com.neoutils.finsight.domain.model

/** A `(transactionId, currency)` pair whose entries do not sum to zero. */
data class UnbalancedTransaction(
    val transactionId: Long,
    val currency: String,
    val sum: Long,
)
