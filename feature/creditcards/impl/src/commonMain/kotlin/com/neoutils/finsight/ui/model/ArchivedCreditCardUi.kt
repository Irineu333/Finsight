package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.extension.DisplayAmount

/**
 * A flat, display-ready view of an archived card — the fields the archived listing
 * and its detail render. Carries no domain graph, so the domain [CreditCard] stays
 * on the ViewModel side of the boundary and never reaches a Composable.
 */
data class ArchivedCreditCardUi(
    val cardId: Long,
    val iconKey: String,
    val name: String,
    // Denominated by the card's own `LIABILITY` account (design D17), and exact:
    // a limit is one account's figure and nothing converted it.
    val limit: DisplayAmount,
    val closingDay: Int,
    val dueDay: Int,
)

fun CreditCard.toArchivedUi(currency: String) = ArchivedCreditCardUi(
    cardId = id,
    iconKey = iconKey,
    name = name,
    limit = DisplayAmount.magnitude(limit, currency, isApproximate = false),
    closingDay = closingDay,
    dueDay = dueDay,
)
