package com.neoutils.finance.ui.modal.editCreditCardLimit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.usecase.GetCreditCardLimitUseCase
import com.neoutils.finance.domain.usecase.SetCreditCardLimitUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditCreditCardLimitViewModel(
    private val getCreditCardLimitUseCase: GetCreditCardLimitUseCase,
    private val setCreditCardLimitUseCase: SetCreditCardLimitUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditCreditCardLimitUiState())
    val uiState: StateFlow<EditCreditCardLimitUiState> = _uiState.asStateFlow()

    init {
        loadCurrentLimit()
    }

    private fun loadCurrentLimit() {
        val currentLimit = getCreditCardLimitUseCase()
        _uiState.value = _uiState.value.copy(currentLimit = currentLimit)
    }

    fun saveLimit(limit: Double) {
        viewModelScope.launch {
            setCreditCardLimitUseCase(limit)
            modalManager.dismiss()
        }
    }
}

data class EditCreditCardLimitUiState(
    val currentLimit: Double = 0.0
)
