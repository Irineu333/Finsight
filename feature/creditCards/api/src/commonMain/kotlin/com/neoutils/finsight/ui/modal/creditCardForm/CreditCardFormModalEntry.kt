package com.neoutils.finsight.ui.modal.creditCardForm

import com.neoutils.finsight.core.domain.model.CreditCard
import com.neoutils.finsight.ui.component.ModalBottomSheet

interface CreditCardFormModalEntry {
    fun create(creditCard: CreditCard? = null): ModalBottomSheet
}
