@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.editBalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finance.domain.usecase.AdjustCreditCardBillUseCase
import com.neoutils.finance.domain.usecase.AdjustFinalBalanceUseCase
import com.neoutils.finance.domain.usecase.AdjustInitialBalanceUseCase
import com.neoutils.finance.extension.toYearMonth
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
    private val creditCardId: Long?,
    private val adjustBalanceUseCase: AdjustBalanceUseCase,
    private val adjustFinalBalanceUseCase: AdjustFinalBalanceUseCase,
    private val adjustInitialBalanceUseCase: AdjustInitialBalanceUseCase,
    private val adjustCreditCardBillUseCase: AdjustCreditCardBillUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    private val timZone get() = TimeZone.currentSystemDefault()
    private val currentDateTime get() = Clock.System.now().toLocalDateTime(timZone)

    fun adjustBalance(targetBalance: Double) = viewModelScope.launch {
        when (type) {
            EditBalanceModal.Type.CURRENT -> {
                adjustBalanceUseCase(
                    targetBalance = targetBalance,
                    adjustmentDate = currentDateTime.date
                )
            }

            EditBalanceModal.Type.FINAL -> {
                adjustFinalBalanceUseCase(
                    targetBalance = targetBalance,
                    targetMonth = targetMonth
                )
            }

            EditBalanceModal.Type.INITIAL -> {
                adjustInitialBalanceUseCase(
                    targetBalance = targetBalance,
                    targetMonth = targetMonth
                )
            }

            EditBalanceModal.Type.CREDIT_CARD -> {
                if (creditCardId != null) {
                    adjustCreditCardBillUseCase(
                        creditCardId = creditCardId,
                        targetBill = targetBalance,
                        adjustmentDate = currentDateTime.date
                    )
                }
            }
        }
        modalManager.dismiss()
    }
}

