package com.neoutils.finance.ui.screen.installments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.repository.IInstallmentRepository
import com.neoutils.finance.domain.repository.IOperationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class InstallmentsViewModel(
    installmentRepository: IInstallmentRepository,
    operationRepository: IOperationRepository,
) : ViewModel() {

    private val selectedInstallmentIndex = MutableStateFlow(0)

    val uiState = combine(
        installmentRepository.observeAllInstallments(),
        operationRepository.observeAllOperations(),
        selectedInstallmentIndex,
    ) { installments, operations, selectedIndex ->
        val operationsByInstallmentId = operations
            .filter { it.installment != null }
            .groupBy { checkNotNull(it.installment).id }

        val installmentsUi = installments
            .mapNotNull { installment ->
                val installmentOperations = operationsByInstallmentId[installment.id]
                    .orEmpty()
                    .sortedBy { it.installment?.number ?: Int.MAX_VALUE }

                if (installmentOperations.isEmpty()) {
                    null
                } else {
                    InstallmentWithOperationsUi(
                        installment = installment,
                        operations = installmentOperations,
                        latestOperationDate = installmentOperations.maxOf { it.date },
                    )
                }
            }
            .sortedWith(
                compareByDescending<InstallmentWithOperationsUi> { it.latestOperationDate }
                    .thenByDescending { it.installment.id }
            )

        val safeSelectedIndex = selectedIndex
            .coerceAtLeast(0)
            .coerceAtMost((installmentsUi.size - 1).coerceAtLeast(0))

        InstallmentsUiState(
            installments = installmentsUi,
            selectedInstallmentIndex = safeSelectedIndex,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InstallmentsUiState(),
    )

    fun onAction(action: InstallmentsAction) {
        when (action) {
            is InstallmentsAction.SelectInstallment -> {
                selectedInstallmentIndex.update { action.index.coerceAtLeast(0) }
            }
        }
    }
}
