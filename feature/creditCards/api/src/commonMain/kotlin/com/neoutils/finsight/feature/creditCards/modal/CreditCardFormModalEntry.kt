package com.neoutils.finsight.feature.creditCards.modal

import com.neoutils.finsight.core.ui.component.ModalBottomSheet

interface CreditCardFormModalEntry {
    fun create(creditCardId: Long? = null): ModalBottomSheet
}
