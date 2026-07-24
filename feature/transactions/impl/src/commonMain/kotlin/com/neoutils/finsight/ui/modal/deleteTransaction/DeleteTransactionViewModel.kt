package com.neoutils.finsight.ui.modal.deleteTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.transaction_error_generic
import com.neoutils.finsight.util.UiText
import com.neoutils.finsight.domain.analytics.event.DeleteTransaction
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.usecase.DeleteTransactionUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteTransactionViewModel(
    private val transaction: Transaction,
    private val categoryRepository: ICategoryRepository,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    fun deleteTransaction() = viewModelScope.launch {
        // The analytics event still reports the category by name; the ledger only
        // hands out its dimension, so the name is resolved here (design D6).
        val categoryName = transaction.nominalDimensionId
            ?.let { dimensionId ->
                categoryRepository.getCategoryByDimensionId(dimensionId)
            }
            ?.name
        deleteTransactionUseCase(transaction).onRight {
            analytics.logEvent(DeleteTransaction(transaction, categoryName))
            modalManager.dismissAll()
        }.onLeft {
            crashlytics.recordException(it)
            modalManager.showError(it.toUiMessage())
        }
    }

    /**
     * A refused deletion has a reason the user can act on — a locked invoice, an
     * archived account whose balance the removal would reopen. Without this the
     * sheet just did not close and said nothing, which in a finance app reads as
     * "it worked".
     */
    private fun Throwable.toUiMessage(): UiText = when (this) {
        is InvoiceException -> error.toUiText()
        is ClosedAccountException -> error.toUiText()
        else -> UiText.Res(Res.string.transaction_error_generic)
    }
}
