package com.neoutils.finsight.feature.transactions.modal.viewAdjustment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.domain.model.Operation
import com.neoutils.finsight.feature.accounts.repository.IAccountRepository
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.creditCards.repository.IInvoiceRepository
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class ViewAdjustmentViewModel(
    operation: Operation,
    operationRepository: IOperationRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
) : ViewModel() {

    val uiState = flow {
        val current = operationRepository.getOperationById(operation.id) ?: operation
        val tx = current.primaryTransaction
        emit(
            ViewAdjustmentUiState(
                operation = current,
                account = tx.accountId?.let { accountRepository.getAccountById(it) },
                creditCard = tx.creditCardId?.let { creditCardRepository.getCreditCardById(it) },
                invoice = tx.invoiceId?.let { invoiceRepository.getInvoiceById(it) },
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewAdjustmentUiState(operation = operation)
    )
}
