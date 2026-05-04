package com.neoutils.finsight.feature.transactions.modal.viewTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.OperationPerspective
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.accounts.repository.IAccountRepository
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.creditCards.repository.IInvoiceRepository
import com.neoutils.finsight.feature.recurring.repository.IRecurringRepository
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ViewOperationViewModel(
    operation: Operation,
    private val perspective: OperationPerspective? = null,
    operationRepository: IOperationRepository,
    private val recurringRepository: IRecurringRepository,
    private val accountRepository: IAccountRepository,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
) : ViewModel() {

    private val _events = Channel<ViewOperationEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = flow {
        val currentOperation = operationRepository.getOperationById(operation.id) ?: operation
        val tx = perspective?.resolve(currentOperation) ?: currentOperation.primaryTransaction
        val category = tx.categoryId?.let { categoryRepository.getCategoryById(it) }
        val account = tx.accountId?.let { accountRepository.getAccountById(it) }
        val creditCard = tx.creditCardId?.let { creditCardRepository.getCreditCardById(it) }
        val invoice = tx.invoiceId?.let { invoiceRepository.getInvoiceById(it) }
        val sourceAccount = currentOperation.transactions
            .firstOrNull { it.type == Transaction.Type.EXPENSE && it.target == Transaction.Target.ACCOUNT }
            ?.accountId
            ?.let { accountRepository.getAccountById(it) }
        val destinationAccount = currentOperation.transactions
            .firstOrNull { it.type == Transaction.Type.INCOME && it.target == Transaction.Target.ACCOUNT }
            ?.accountId
            ?.let { accountRepository.getAccountById(it) }
        emit(
            ViewOperationUiState(
                operation = currentOperation,
                perspective = perspective,
                category = category,
                account = account,
                creditCard = creditCard,
                invoice = invoice,
                sourceAccount = sourceAccount,
                destinationAccount = destinationAccount,
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewOperationUiState(
            operation = operation,
            perspective = perspective,
        )
    )

    fun onAction(action: ViewOperationAction) = viewModelScope.launch {
        when (action) {
            is ViewOperationAction.OpenRecurring -> {
                val recurring = recurringRepository.observeAllRecurring().first()
                    .firstOrNull { it.id == action.recurringId }
                    ?: return@launch
                _events.send(ViewOperationEvent.OpenRecurring(recurring))
            }
        }
    }
}
