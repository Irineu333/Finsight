@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.editBalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finance.domain.usecase.AdjustFinalBalanceUseCase
import com.neoutils.finance.domain.usecase.AdjustInitialBalanceUseCase
import com.neoutils.finance.domain.usecase.AdjustInvoiceUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class EditBalanceViewModel(
    private val type: EditBalanceModal.Type,
    private val targetMonth: YearMonth,
    private val invoiceId: Long?,
    private val adjustBalanceUseCase: AdjustBalanceUseCase,
    private val adjustFinalBalanceUseCase: AdjustFinalBalanceUseCase,
    private val adjustInitialBalanceUseCase: AdjustInitialBalanceUseCase,
    private val adjustInvoiceUseCase: AdjustInvoiceUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    private val timeZone get() = TimeZone.currentSystemDefault()
    private val currentDate get() = Clock.System.now().toLocalDateTime(timeZone).date

    fun adjustBalance(targetBalance: Double) = viewModelScope.launch {
        when (type) {
            EditBalanceModal.Type.CURRENT -> adjustBalanceUseCase(
                targetBalance = targetBalance,
                adjustmentDate = currentDate
            )

            EditBalanceModal.Type.FINAL -> adjustFinalBalanceUseCase(
                targetBalance = targetBalance,
                targetMonth = targetMonth
            )

            EditBalanceModal.Type.INITIAL -> adjustInitialBalanceUseCase(
                targetBalance = targetBalance,
                targetMonth = targetMonth
            )

            EditBalanceModal.Type.CREDIT_CARD -> adjustInvoiceUseCase(
                invoiceId = invoiceId ?: return@launch,
                target = targetBalance,
                adjustmentDate = currentDate
            )
        }

        modalManager.dismiss()
    }
}
