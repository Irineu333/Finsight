package com.neoutils.finsight.feature.transactions.api

import com.neoutils.finsight.domain.model.Transaction
import kotlinx.serialization.Serializable

@Serializable
data class TransactionsRoute(
    val filterType: Transaction.Type? = null,
    val filterTarget: Transaction.Target? = null,
)
