package com.neoutils.finsight.feature.creditcards.impl

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.feature.creditcards.api.CreditCardsEntry
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.modal.creditCardForm.CreditCardFormModal

internal class CreditCardsEntryImpl : CreditCardsEntry {
    override fun creditCardFormModal(creditCard: CreditCard?): Modal = CreditCardFormModal(creditCard)
}
