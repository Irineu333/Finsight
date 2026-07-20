package com.neoutils.finsight.feature.transactions.api

import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.navigation.NavRoute
import kotlinx.serialization.Serializable

@Serializable
data class TransactionsRoute(
    val filterType: TransactionType? = null,
    val filterTarget: TransactionTarget? = null,
) : NavRoute
