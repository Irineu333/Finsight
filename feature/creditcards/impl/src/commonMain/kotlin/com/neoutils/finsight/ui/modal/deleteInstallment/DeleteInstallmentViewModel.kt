package com.neoutils.finsight.ui.modal.deleteInstallment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteInstallments
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.usecase.DeleteInstallmentUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteInstallmentViewModel(
    private val installment: Installment,
    private val transactions: List<Transaction>,
    private val deleteInstallmentUseCase: DeleteInstallmentUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    fun deleteInstallment() = viewModelScope.launch {
        deleteInstallmentUseCase(installment, transactions).onRight {
            analytics.logEvent(DeleteInstallments(installment, transactions))
            modalManager.dismissAll()
        }.onLeft {
            crashlytics.recordException(it)
        }
    }
}
