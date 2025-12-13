package com.neoutils.finance.ui.screen.creditCards

import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.ui.model.CreditCardBillUi

data class CreditCardsUiState(
    val creditCards: List<CreditCardWithBill> = emptyList()
)

data class CreditCardWithBill(
    val creditCard: CreditCard,
    val billUi: CreditCardBillUi,
    val billAmount: Double
)
