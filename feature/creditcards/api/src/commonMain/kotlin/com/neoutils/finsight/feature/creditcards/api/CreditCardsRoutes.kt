package com.neoutils.finsight.feature.creditcards.api

import kotlinx.serialization.Serializable

@Serializable
data class CreditCardsRoute(val creditCardId: Long? = null)

@Serializable
data class InvoiceTransactionsRoute(val creditCardId: Long)

@Serializable
data object InstallmentsRoute
