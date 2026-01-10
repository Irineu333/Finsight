package com.neoutils.finance.ui.screen.creditCards

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction

sealed class CreditCardsAction {
    data class SelectCard(val index: Int) : CreditCardsAction()
    data class SelectCategory(val category: Category?) : CreditCardsAction()
    data class SelectType(val type: Transaction.Type?) : CreditCardsAction()
}
