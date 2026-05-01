package com.neoutils.finsight.ui.modal.creditCardForm

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.ui.component.ModalBottomSheet

class CreditCardFormModalEntryImpl : CreditCardFormModalEntry {
    override fun create(creditCard: CreditCard?): ModalBottomSheet =
        CreditCardFormModal(creditCard = creditCard)
}
