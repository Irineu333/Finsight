package com.neoutils.finsight.ui.modal.budgetForm

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CurrencyInfo
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.util.Validation

data class BudgetFormUiState(
    val availableCategories: List<Category> = emptyList(),
    val selectedCategories: List<Category> = emptyList(),
    val selectedIcon: AppIcon = AppIcon.BUDGET,
    val title: String = "",
    val amount: String = "",
    /**
     * What [amount] will be denominated in: the currency of the budget being edited, or
     * of the default account when creating one — where the user actually spends, and
     * never the base currency, which only says where he reads totals (design D13).
     *
     * `null` only while it is still being resolved, and [canSubmit] refuses until it is:
     * a limit with an invented denomination is the failure this field exists to prevent.
     * The control that lets a multi-currency user choose it comes later (task 12.5); a
     * user with one currency never sees one, because there is nothing to choose.
     */
    val currency: String? = null,
    /**
     * Whether the form offers the choice at all — true only when the user holds more
     * than one currency and the budget is being created (design D13). With one
     * currency there is nothing to choose, and the form stays the one it always was.
     */
    val canChangeCurrency: Boolean = false,
    val selectableCurrencies: List<CurrencyInfo> = emptyList(),
    val validation: Map<BudgetField, Validation> = mapOf(),
    val isEditMode: Boolean = false,
    val limitType: LimitType = LimitType.FIXED,
    val percentage: String = "",
    val incomeRecurrings: List<Recurring> = emptyList(),
    val selectedRecurring: Recurring? = null,
) {
    val canSubmit: Boolean
        get() = validation[BudgetField.TITLE] == Validation.Valid &&
            currency != null &&
            selectedCategories.isNotEmpty() &&
            when (limitType) {
                LimitType.FIXED -> amount.moneyToDouble() > 0
                LimitType.PERCENTAGE ->
                    percentage.toDoubleOrNull()?.let { it > 0 && it <= 100 } == true &&
                    selectedRecurring != null
            }
}

enum class BudgetField {
    TITLE
}
