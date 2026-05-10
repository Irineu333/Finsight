package com.neoutils.finsight.feature.transactions.modal.viewTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.feature.accounts.repository.IAccountRepository
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.creditCards.repository.IInvoiceRepository
import com.neoutils.finsight.feature.transactions.error.OperationError
import com.neoutils.finsight.feature.transactions.exception.OperationException
import com.neoutils.finsight.feature.transactions.model.OperationPerspective
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ViewOperationViewModel(
    private val operationId: Long,
    private val perspective: OperationPerspective? = null,
    private val operationRepository: IOperationRepository,
    private val accountRepository: IAccountRepository,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val _events = Channel<ViewOperationEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = flow {
        val operation = operationRepository.getOperationById(operationId)

        if (operation == null) {
            crashlytics.recordException(OperationException(OperationError.NOT_FOUND))
            emit(ViewOperationUiState.Error)
            return@flow
        }

        val tx = perspective?.resolve(operation) ?: operation.primaryTransaction

        coroutineScope {
            val category = tx.categoryId?.let { id -> async { categoryRepository.getCategoryById(id) } }
            val account = tx.accountId?.let { id -> async { accountRepository.getAccountById(id) } }
            val creditCard = tx.creditCardId?.let { id -> async { creditCardRepository.getCreditCardById(id) } }
            val invoice = tx.invoiceId?.let { id -> async { invoiceRepository.getInvoiceById(id) } }

            val sourceAccount = operation.transactions
                .firstOrNull { it.type == Transaction.Type.EXPENSE && it.target == Transaction.Target.ACCOUNT }
                ?.accountId
                ?.let { id -> async { accountRepository.getAccountById(id) } }

            val destinationAccount = operation.transactions
                .firstOrNull { it.type == Transaction.Type.INCOME && it.target == Transaction.Target.ACCOUNT }
                ?.accountId
                ?.let { id -> async { accountRepository.getAccountById(id) } }

            emit(
                ViewOperationUiState.Content(
                    operation = operation,
                    perspective = perspective,
                    category = category?.await(),
                    account = account?.await(),
                    creditCard = creditCard?.await(),
                    invoice = invoice?.await(),
                    sourceAccount = sourceAccount?.await(),
                    destinationAccount = destinationAccount?.await(),
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewOperationUiState.Loading,
    )

    fun onAction(action: ViewOperationAction) = viewModelScope.launch {
        when (action) {
            is ViewOperationAction.OpenRecurring -> {
                _events.send(ViewOperationEvent.OpenRecurring(action.recurringId))
            }
        }
    }
}
