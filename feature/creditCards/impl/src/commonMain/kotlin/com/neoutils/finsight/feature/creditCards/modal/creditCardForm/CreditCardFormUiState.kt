package com.neoutils.finsight.feature.creditCards.modal.creditCardForm

import com.neoutils.finsight.feature.creditCards.model.form.CreditCardForm
import com.neoutils.finsight.core.ui.util.Validation

sealed class CreditCardFormUiState {

    data object Loading : CreditCardFormUiState()

    data class Content(
        val form: CreditCardForm,
        val validation: Map<CreditCardField, Validation>,
        val isEditMode: Boolean,
        val canSubmit: Boolean,
    ) : CreditCardFormUiState()
}

enum class CreditCardField {
    NAME,
    LIMIT,
    CLOSING_DAY,
    DUE_DAY,
}
