package com.neoutils.finsight.ui.modal.deleteInstallment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteInstallments
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteInstallmentViewModel(
    private val installment: Installment,
    private val transactions: List<Transaction>,
    private val transactionRepository: ITransactionRepository,
    private val installmentRepository: IInstallmentRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
) : ViewModel() {

    fun deleteInstallment() = viewModelScope.launch {
        transactions.forEach { transaction ->
            transactionRepository.deleteTransactionById(transaction.id)
        }
        installmentRepository.deleteInstallmentById(installment.id)
        analytics.logEvent(DeleteInstallments(installment, transactions))
        modalManager.dismissAll()
    }
}
