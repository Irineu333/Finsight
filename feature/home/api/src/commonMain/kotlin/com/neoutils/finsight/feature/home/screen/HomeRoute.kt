package com.neoutils.finsight.feature.home.screen

import com.neoutils.finsight.feature.transactions.model.Transaction
import kotlinx.serialization.Serializable

sealed class HomeRoute {
    @Serializable
    data object Dashboard : HomeRoute()

    @Serializable
    data class Transactions(
        val filterType: Transaction.Type? = null,
        val filterTarget: Transaction.Target? = null
    ) : HomeRoute()
}
