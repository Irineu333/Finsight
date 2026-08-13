package com.neoutils.finsight.ui.screen.creditCards

import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.TransactionType

sealed class CreditCardsAction {
    data class SelectCard(val index: Int) : CreditCardsAction()
    /** Selects a value of the analytic axis, or `null` for the neutral state. */
    data class SelectSubject(val subject: SpendingSubject?) : CreditCardsAction()
    data class SelectType(val type: TransactionType?) : CreditCardsAction()
    data class ToggleRecurring(val enabled: Boolean) : CreditCardsAction()
    data class ToggleInstallment(val enabled: Boolean) : CreditCardsAction()

    /** Returns the list filters to neutral. The selected card is not a filter. */
    data object ClearFilters : CreditCardsAction()
}
