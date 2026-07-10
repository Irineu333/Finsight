package com.neoutils.finsight.feature.creditcards.api

import com.neoutils.finsight.navigation.NavRoute
import kotlinx.serialization.Serializable

@Serializable
data class CreditCardsRoute(val creditCardId: Long? = null) : NavRoute

@Serializable
data class InvoiceTransactionsRoute(val creditCardId: Long) : NavRoute

@Serializable
data object InstallmentsRoute : NavRoute
