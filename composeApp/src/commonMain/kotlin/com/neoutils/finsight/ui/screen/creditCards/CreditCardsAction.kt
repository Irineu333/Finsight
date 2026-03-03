package com.neoutils.finsight.ui.screen.creditCards

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction

sealed class CreditCardsAction {
    data class SelectCard(val index: Int) : CreditCardsAction()
    data class SelectCategory(val category: Category?) : CreditCardsAction()
    data class SelectType(val type: Transaction.Type?) : CreditCardsAction()
    data class SelectRecurring(val recurring: Recurring?) : CreditCardsAction()
}
