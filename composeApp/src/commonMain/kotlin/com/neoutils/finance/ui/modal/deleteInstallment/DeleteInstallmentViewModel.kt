package com.neoutils.finance.ui.modal.deleteInstallment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Installment
import com.neoutils.finance.domain.model.Operation
import com.neoutils.finance.domain.repository.IInstallmentRepository
import com.neoutils.finance.domain.repository.IOperationRepository
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteInstallmentViewModel(
    private val installment: Installment,
    private val operations: List<Operation>,
    private val operationRepository: IOperationRepository,
    private val installmentRepository: IInstallmentRepository,
    private val modalManager: ModalManager,
) : ViewModel() {

    fun deleteInstallment() = viewModelScope.launch {
        operations.forEach { operation ->
            operationRepository.deleteOperationById(operation.id)
        }
        installmentRepository.deleteInstallmentById(installment.id)
        modalManager.dismissAll()
    }
}
