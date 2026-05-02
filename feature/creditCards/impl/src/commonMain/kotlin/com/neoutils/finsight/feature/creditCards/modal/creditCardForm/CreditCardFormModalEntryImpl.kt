package com.neoutils.finsight.feature.creditCards.modal.creditCardForm

import com.neoutils.finsight.core.domain.model.CreditCard
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
class CreditCardFormModalEntryImpl : CreditCardFormModalEntry {
    override fun create(creditCard: CreditCard?): ModalBottomSheet =
        CreditCardFormModal(creditCard = creditCard)
}
