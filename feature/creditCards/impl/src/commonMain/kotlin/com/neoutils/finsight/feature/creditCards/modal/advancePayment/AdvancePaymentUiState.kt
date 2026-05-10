package com.neoutils.finsight.feature.creditCards.modal.advancePayment

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.creditCards.model.form.AdvancePaymentForm

sealed interface AdvancePaymentUiState {
    data object Loading : AdvancePaymentUiState
    data object Error : AdvancePaymentUiState
    data class Content(
        val form: AdvancePaymentForm,
        val accounts: List<Account>,
    ) : AdvancePaymentUiState
}
