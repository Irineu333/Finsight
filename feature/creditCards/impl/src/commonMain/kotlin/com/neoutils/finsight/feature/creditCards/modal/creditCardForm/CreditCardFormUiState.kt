package com.neoutils.finsight.feature.creditCards.modal.creditCardForm

import com.neoutils.finsight.feature.creditCards.model.form.CreditCardForm
import com.neoutils.finsight.core.ui.util.AppIcon
import com.neoutils.finsight.core.ui.util.Validation

data class CreditCardFormUiState(
    val form: CreditCardForm = CreditCardForm(),
    val selectedIcon: AppIcon = AppIcon.CARD,
    val validation: Map<CreditCardField, Validation> = mapOf(),
    val isEditMode: Boolean = false,
    val canSubmit: Boolean = false,
)

enum class CreditCardField {
    NAME,
    LIMIT,
    CLOSING_DAY,
    DUE_DAY,
}
