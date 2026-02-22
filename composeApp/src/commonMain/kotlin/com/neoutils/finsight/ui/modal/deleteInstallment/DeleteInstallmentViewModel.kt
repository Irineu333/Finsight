package com.neoutils.finsight.ui.modal.deleteInstallment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.ui.component.ModalManager
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
