package com.neoutils.finance.ui.modal.editInvoiceBalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.usecase.AdjustInvoiceUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class EditInvoiceBalanceViewModel(
    private val invoiceId: Long,
    private val adjustInvoiceUseCase: AdjustInvoiceUseCase,
    private val invoiceRepository: IInvoiceRepository,
    private val modalManager: ModalManager
) : ViewModel() {

    private val timeZone get() = TimeZone.currentSystemDefault()
    private val currentDate get() = Clock.System.now().toLocalDateTime(timeZone).date

    fun adjustBalance(targetBalance: Double) = viewModelScope.launch {
        val invoice = invoiceRepository.getInvoiceById(invoiceId)

        adjustInvoiceUseCase(
            invoice = checkNotNull(invoice),
            target = targetBalance,
            adjustmentDate = currentDate
        )

        modalManager.dismiss()
    }
}
