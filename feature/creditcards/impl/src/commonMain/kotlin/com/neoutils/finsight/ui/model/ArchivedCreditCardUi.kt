package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.CreditCard

/**
 * A flat, display-ready view of an archived card — the fields the archived listing
 * and its detail render. Carries no domain graph, so the domain [CreditCard] stays
 * on the ViewModel side of the boundary and never reaches a Composable.
 */
data class ArchivedCreditCardUi(
    val cardId: Long,
    val iconKey: String,
    val name: String,
    val limit: Double,
    val closingDay: Int,
    val dueDay: Int,
)

fun CreditCard.toArchivedUi() = ArchivedCreditCardUi(
    cardId = id,
    iconKey = iconKey,
    name = name,
    limit = limit,
    closingDay = closingDay,
    dueDay = dueDay,
)
