package com.neoutils.finsight.ui.modal.creditCardForm

import com.neoutils.finsight.domain.model.form.CreditCardForm
import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.util.Validation

data class CreditCardFormUiState(
    // No default: a form has to be built by someone who decided its currency.
    val form: CreditCardForm,
    val selectedIcon: AppIcon = AppIcon.CARD,
    val validation: Map<CreditCardField, Validation> = mapOf(),
    val isEditMode: Boolean = false,
    val canSubmit: Boolean = false,
    /**
     * Decided by the mode of the form, not by the state of the card (design D12): a selector
     * when creating, a locked state line when editing, always.
     */
    val canChangeCurrency: Boolean = false,
)

enum class CreditCardField {
    NAME,
    LIMIT,
    CLOSING_DAY,
    DUE_DAY,
}
