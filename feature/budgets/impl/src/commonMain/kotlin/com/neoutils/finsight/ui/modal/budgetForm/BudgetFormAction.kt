package com.neoutils.finsight.ui.modal.budgetForm

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.util.AppIcon

sealed class BudgetFormAction {
    data class TitleChanged(val title: String) : BudgetFormAction()
    data class CategoryToggled(val category: Category) : BudgetFormAction()
    data class AmountChanged(val amount: String) : BudgetFormAction()
    data class IconSelected(val icon: AppIcon) : BudgetFormAction()
    data class LimitTypeChanged(val limitType: LimitType) : BudgetFormAction()
    data class PercentageChanged(val percentage: String) : BudgetFormAction()
    data class RecurringSelected(val recurring: Recurring) : BudgetFormAction()
    /** Only sent when the form offered the choice — see `BudgetFormUiState`. */
    data class CurrencySelected(val code: String) : BudgetFormAction()

    data object Submit : BudgetFormAction()
}
