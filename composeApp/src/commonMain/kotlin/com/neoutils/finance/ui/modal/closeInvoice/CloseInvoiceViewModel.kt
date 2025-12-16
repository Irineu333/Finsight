package com.neoutils.finance.ui.modal.closeInvoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.usecase.CloseInvoiceUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class CloseInvoiceViewModel(
    private val invoiceId: Long,
    private val closeInvoiceUseCase: CloseInvoiceUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloseInvoiceUiState())
    val uiState: StateFlow<CloseInvoiceUiState> = _uiState.asStateFlow()

    fun closeInvoice(closingDate: LocalDate) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            closeInvoiceUseCase(invoiceId).fold(
                onSuccess = {
                    modalManager.dismissAll()
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = it.message
                    )
                }
            )
        }
    }
}

data class CloseInvoiceUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)
