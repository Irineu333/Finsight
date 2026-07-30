package com.neoutils.finsight.ui.modal.budgetForm

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.LAST_RESORT_CURRENCY
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
    val validation: Map<BudgetField, Validation> = mapOf(),
    val isEditMode: Boolean = false,
    val limitType: LimitType = LimitType.FIXED,
    val percentage: String = "",
    val incomeRecurrings: List<Recurring> = emptyList(),
    val selectedRecurring: Recurring? = null,
    /**
     * The currency the limit is stated in — always known, never absent.
     *
     * With one currency among the accounts it is that one, and [offersCurrencyChoice] is
     * false, so the form shows **no control at all** and looks exactly as it did before
     * currencies existed. That is the point of the flag: the budget form creates no currency,
     * it only chooses among the ones the accounts already have, and with one there is nothing
     * to choose — the value is the only possible answer rather than a silent default.
     *
     * This is why it differs from the account form, where the currency line is always
     * visible: that form is the one door a second currency is born through, so it has to stay
     * open even while there is one.
     */
    val currency: String = LAST_RESORT_CURRENCY,
    val offeredCurrencies: List<String> = emptyList(),
) {
    /** A choice is offered only when there is one to make (design D13). */
    val offersCurrencyChoice: Boolean get() = !isEditMode && offeredCurrencies.size > 1

    /**
     * On edit the currency is shown locked, for the reason a limit's denomination never
     * changes: reinterpreting a number the user typed is rewriting what they meant.
     */
    val isCurrencyLocked: Boolean get() = isEditMode
    val canSubmit: Boolean
        get() = validation[BudgetField.TITLE] == Validation.Valid &&
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
