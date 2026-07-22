package com.neoutils.finsight.ui.modal.deleteInstallment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteInstallments
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.usecase.DeleteInstallmentUseCase
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.ledger_action_error_generic
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.UiText
import kotlinx.coroutines.launch

class DeleteInstallmentViewModel(
    private val installment: Installment,
    private val transactions: List<Transaction>,
    private val categoryRepository: ICategoryRepository,
    private val deleteInstallmentUseCase: DeleteInstallmentUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    fun deleteInstallment() = viewModelScope.launch {
        // The event still reports the category by name; the ledger hands out only
        // the dimension its nominal leg carries (design D6).
        val categoryName = transactions.firstOrNull()?.nominalDimensionId
            ?.let { dimensionId ->
                categoryRepository.getAllCategoriesIncludingClosed().firstOrNull { it.dimensionId == dimensionId }
            }
            ?.name
        deleteInstallmentUseCase(installment, transactions).onRight {
            analytics.logEvent(DeleteInstallments(installment, categoryName))
            modalManager.dismissAll()
        }.onLeft {
            crashlytics.recordException(it)
            modalManager.showError(it.toUiMessage())
        }
    }

    /**
     * A refused deletion has a reason the user can act on — a locked invoice, or a
     * cartão arquivado whose balance the removal would reopen. Without this the sheet
     * just did not close and said nothing, which reads as "it worked".
     */
    private fun Throwable.toUiMessage(): UiText = when (this) {
        is InvoiceException -> error.toUiText()
        is ClosedAccountException -> error.toUiText()
        else -> UiText.Res(Res.string.ledger_action_error_generic)
    }
}
