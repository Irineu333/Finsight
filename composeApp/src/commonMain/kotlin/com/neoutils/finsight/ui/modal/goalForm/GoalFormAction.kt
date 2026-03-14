package com.neoutils.finsight.ui.modal.goalForm

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.util.AppIcon

sealed class GoalFormAction {
    data class TitleChanged(val title: String) : GoalFormAction()
    data class CategoryToggled(val category: Category) : GoalFormAction()
    data class AmountChanged(val amount: String) : GoalFormAction()
    data class IconSelected(val icon: AppIcon) : GoalFormAction()
    data object Submit : GoalFormAction()
}
