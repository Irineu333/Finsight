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
     * A user with one currency never sees a control for it, because there is nothing to
     * choose.
     */
    val currency: String? = null,
    /**
     * Whether the form shows the currency row at all — true when the user holds more
     * than one currency (design D13). With one currency there is nothing to choose, and
     * the form stays exactly the one it always was, not a control more.
     */
    val hasCurrencyChoice: Boolean = false,
    /**
     * Whether that row is a picker rather than a locked state — true only while the
     * budget is being **created**. Editing shows the stored denomination locked, for the
     * reason of design D12: reinterpreting a saved limit silently rewrites the meaning of
     * a number the user typed, so changing it means creating another budget.
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
