package com.neoutils.finsight.feature.creditCards.entryPoint

import com.neoutils.finsight.core.domain.model.CreditCard
import com.neoutils.finsight.feature.creditCards.modal.CreditCardFormModalEntry
import com.neoutils.finsight.feature.creditCards.modal.creditCardForm.CreditCardFormModal

class CreditCardFormModalEntryImpl : CreditCardFormModalEntry {
    override fun create(creditCard: CreditCard?) = CreditCardFormModal(creditCard = creditCard)
}