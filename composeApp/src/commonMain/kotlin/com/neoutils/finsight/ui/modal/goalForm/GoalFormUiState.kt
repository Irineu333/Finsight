package com.neoutils.finsight.ui.modal.goalForm

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.util.Validation

data class GoalFormUiState(
    val availableCategories: List<Category> = emptyList(),
    val selectedCategories: List<Category> = emptyList(),
    val selectedIcon: AppIcon = AppIcon.GOAL,
    val title: String = "",
    val amount: String = "",
    val validation: Map<GoalField, Validation> = mapOf(),
    val isEditMode: Boolean = false,
) {
    val canSubmit: Boolean
        get() = validation[GoalField.TITLE] == Validation.Valid &&
            selectedCategories.isNotEmpty() &&
            amount.moneyToDouble() > 0
}

enum class GoalField {
    TITLE
}
