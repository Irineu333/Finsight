package com.neoutils.finsight.feature.creditcards.api

import com.neoutils.finsight.navigation.NavRoute
import kotlinx.serialization.Serializable

@Serializable
data class CreditCardsRoute(val creditCardId: Long? = null) : NavRoute

/**
 * The statement of a card's invoices.
 *
 * [invoiceId] is which one to open on. Absent, the screen opens on the invoice it
 * opens on by default — the caller had no invoice in mind, which is the case of a
 * card tapped in the cards list. A caller that *does* have one — the detail of a
 * transaction, whose liability leg carries the invoice it landed on — states it,
 * because landing on a different invoice than the one just read is a wrong answer,
 * not merely an unhelpful one.
 */
@Serializable
data class InvoiceTransactionsRoute(
    val creditCardId: Long,
    val invoiceId: Long? = null,
) : NavRoute

@Serializable
data object InstallmentsRoute : NavRoute
