package com.neoutils.finsight.feature.creditcards.api

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.ui.component.Modal

interface CreditCardsEntry {
    fun creditCardFormModal(creditCard: CreditCard? = null): Modal
}
