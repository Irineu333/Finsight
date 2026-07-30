package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.extension.Denomination
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
    /**
     * Denominated here rather than at the surface: the card is what the limit is stated in,
     * and a number travelling without its currency is what this whole change exists to make
     * unexpressible.
     */
    val limit: DisplayAmount,
    val closingDay: Int,
    val dueDay: Int,
)

fun CreditCard.toArchivedUi() = ArchivedCreditCardUi(
    cardId = id,
    iconKey = iconKey,
    name = name,
    limit = DisplayAmount.natural(limit, Denomination.exact(currency)),
    closingDay = closingDay,
    dueDay = dueDay,
)
