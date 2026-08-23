package com.neoutils.finsight.ui.modal.deleteCurrency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteCurrency
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.usecase.DeleteCurrencyUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteCurrencyViewModel(
    private val code: String,
    private val deleteCurrency: DeleteCurrencyUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
) : ViewModel() {

    fun delete() = viewModelScope.launch {
        deleteCurrency(code)
            .onRight {
                analytics.logEvent(DeleteCurrency(code))
                modalManager.dismissAll()
            }
            // The refusals — an account or a budget denominates it — reach the user in
            // the one place this app states a refusal, rather than as a dead button.
            .onLeft { modalManager.showError(it.toUiText()) }
    }
}
