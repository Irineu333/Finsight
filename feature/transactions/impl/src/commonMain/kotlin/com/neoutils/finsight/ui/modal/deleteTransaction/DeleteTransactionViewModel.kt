package com.neoutils.finsight.ui.modal.deleteTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteTransaction
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.usecase.DeleteTransactionUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteTransactionViewModel(
    private val transaction: Transaction,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    fun deleteTransaction() = viewModelScope.launch {
        deleteTransactionUseCase(transaction).onRight {
            analytics.logEvent(DeleteTransaction(transaction))
            modalManager.dismissAll()
        }.onLeft {
            crashlytics.recordException(it)
        }
    }
}
