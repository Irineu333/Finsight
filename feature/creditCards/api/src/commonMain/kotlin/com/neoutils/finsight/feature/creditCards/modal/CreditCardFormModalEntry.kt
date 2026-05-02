package com.neoutils.finsight.feature.creditCards.modal

import com.neoutils.finsight.core.domain.model.CreditCard
import com.neoutils.finsight.core.ui.component.ModalBottomSheet

interface CreditCardFormModalEntry {
    fun create(creditCard: CreditCard? = null): ModalBottomSheet
}