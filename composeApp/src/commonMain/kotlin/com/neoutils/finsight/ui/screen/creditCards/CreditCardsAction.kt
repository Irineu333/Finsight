package com.neoutils.finsight.ui.screen.creditCards

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Transaction

sealed class CreditCardsAction {
    data class SelectCard(val index: Int) : CreditCardsAction()
    data class SelectCategory(val category: Category?) : CreditCardsAction()
    data class SelectType(val type: Transaction.Type?) : CreditCardsAction()
    data class ToggleRecurring(val enabled: Boolean) : CreditCardsAction()
    data class ToggleInstallment(val enabled: Boolean) : CreditCardsAction()
}
