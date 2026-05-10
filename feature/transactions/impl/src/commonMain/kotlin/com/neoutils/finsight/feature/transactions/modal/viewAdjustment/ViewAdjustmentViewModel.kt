package com.neoutils.finsight.feature.transactions.modal.viewAdjustment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.feature.accounts.repository.IAccountRepository
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.creditCards.repository.IInvoiceRepository
import com.neoutils.finsight.feature.transactions.error.OperationError
import com.neoutils.finsight.feature.transactions.exception.OperationException
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class ViewAdjustmentViewModel(
    private val operationId: Long,
    private val operationRepository: IOperationRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    val uiState = flow {
        val operation = operationRepository.getOperationById(operationId)

        if (operation == null) {
            crashlytics.recordException(OperationException(OperationError.NOT_FOUND))
            emit(ViewAdjustmentUiState.Error)
            return@flow
        }

        val tx = operation.primaryTransaction

        coroutineScope {
            val account = tx.accountId
                ?.let { id -> async { accountRepository.getAccountById(id) } }
            val creditCard = tx.creditCardId
                ?.let { id -> async { creditCardRepository.getCreditCardById(id) } }
            val invoice = tx.invoiceId
                ?.let { id -> async { invoiceRepository.getInvoiceById(id) } }

            emit(
                ViewAdjustmentUiState.Content(
                    operation = operation,
                    account = account?.await(),
                    creditCard = creditCard?.await(),
                    invoice = invoice?.await(),
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewAdjustmentUiState.Loading,
    )
}
