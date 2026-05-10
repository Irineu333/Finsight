package com.neoutils.finsight.feature.creditCards.entryPoint

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.creditCards.modal.CreditCardFormModalEntry
import com.neoutils.finsight.feature.creditCards.modal.creditCardForm.CreditCardFormModal

class CreditCardFormModalEntryImpl : CreditCardFormModalEntry {
    override fun create(creditCardId: Long?): ModalBottomSheet =
        CreditCardFormModal(creditCardId = creditCardId)
}
