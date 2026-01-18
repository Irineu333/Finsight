@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.editBalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.repository.IAccountRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
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
    private val accountId: Long?,
    private val invoiceId: Long?,
    private val adjustBalanceUseCase: AdjustBalanceUseCase,
    private val adjustFinalBalanceUseCase: AdjustFinalBalanceUseCase,
    private val adjustInitialBalanceUseCase: AdjustInitialBalanceUseCase,
    private val adjustInvoiceUseCase: AdjustInvoiceUseCase,
    private val accountRepository: IAccountRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val modalManager: ModalManager
) : ViewModel() {

    private val timeZone get() = TimeZone.currentSystemDefault()
    private val currentDate get() = Clock.System.now().toLocalDateTime(timeZone).date

    fun adjustBalance(targetBalance: Double) = viewModelScope.launch {
        when (type) {
            EditBalanceModal.Type.CURRENT -> {

                val account = accountId?.let {
                    accountRepository.getAccountById(accountId)
                }

                adjustBalanceUseCase(
                    targetBalance = targetBalance,
                    adjustmentDate = currentDate,
                    account = checkNotNull(account),
                )
            }

            EditBalanceModal.Type.FINAL -> {

                val account = accountId?.let {
                    accountRepository.getAccountById(accountId)
                }

                adjustFinalBalanceUseCase(
                    targetBalance = targetBalance,
                    targetMonth = targetMonth,
                    account = checkNotNull(account),
                )
            }

            EditBalanceModal.Type.INITIAL -> {

                val account = accountId?.let {
                    accountRepository.getAccountById(accountId)
                }

                adjustInitialBalanceUseCase(
                    targetBalance = targetBalance,
                    targetMonth = targetMonth,
                    account = checkNotNull(account),
                )
            }

            EditBalanceModal.Type.CREDIT_CARD -> {

                val invoice = invoiceId?.let {
                    invoiceRepository.getInvoiceById(invoiceId)
                }

                adjustInvoiceUseCase(
                    invoice = checkNotNull(invoice),
                    target = targetBalance,
                    adjustmentDate = currentDate
                )
            }
        }

        modalManager.dismiss()
    }
}
