package com.neoutils.finsight.ui.screen.creditCards

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.TransactionType

sealed class CreditCardsAction {
    data class SelectCard(val index: Int) : CreditCardsAction()
    data class SelectCategory(val category: Category?) : CreditCardsAction()
    data class SelectType(val type: TransactionType?) : CreditCardsAction()
    data class ToggleRecurring(val enabled: Boolean) : CreditCardsAction()
    data class ToggleInstallment(val enabled: Boolean) : CreditCardsAction()
}
