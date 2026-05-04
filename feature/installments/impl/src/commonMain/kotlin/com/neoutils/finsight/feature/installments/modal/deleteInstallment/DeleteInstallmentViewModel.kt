package com.neoutils.finsight.feature.installments.modal.deleteInstallment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.feature.installments.event.DeleteInstallments
import com.neoutils.finsight.feature.installments.model.Installment
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.installments.repository.IInstallmentRepository
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import com.neoutils.finsight.core.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteInstallmentViewModel(
    private val installment: Installment,
    private val operations: List<Operation>,
    private val operationRepository: IOperationRepository,
    private val installmentRepository: IInstallmentRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
) : ViewModel() {

    fun deleteInstallment() = viewModelScope.launch {
        operations.forEach { operation ->
            operationRepository.deleteOperationById(operation.id)
        }
        installmentRepository.deleteInstallmentById(installment.id)
        analytics.logEvent(DeleteInstallments(installment, operations))
        modalManager.dismissAll()
    }
}
