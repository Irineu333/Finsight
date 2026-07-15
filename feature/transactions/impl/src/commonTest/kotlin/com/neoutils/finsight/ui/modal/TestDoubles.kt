package com.neoutils.finsight.ui.modal

import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IOperationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.datetime.LocalDate

class FakeCrashlytics : Crashlytics {
    val recorded = mutableListOf<Throwable>()
    override fun setUserId(id: String?) = Unit
    override fun recordException(e: Throwable) {
        recorded += e
    }
}

class FakeOperationRepository : IOperationRepository {

    private val byId = MutableSharedFlow<Operation?>(replay = 1)

    fun emit(operation: Operation?) {
        byId.tryEmit(operation)
    }

    override fun observeOperationById(id: Long): Flow<Operation?> = byId

    override fun observeAllOperations(): Flow<List<Operation>> = throw NotImplementedError()
    override fun observeOperationsBy(
        date: LocalDate?,
        invoiceId: Long?,
        creditCardId: Long?,
        accountId: Long?,
    ): Flow<List<Operation>> = throw NotImplementedError()
    override suspend fun getAllOperations(): List<Operation> = throw NotImplementedError()
    override suspend fun getOperationById(id: Long): Operation? = throw NotImplementedError()
    override suspend fun createOperation(
        title: String?,
        date: LocalDate,
        categoryId: Long?,
        sourceAccountId: Long?,
        targetCreditCardId: Long?,
        targetInvoiceId: Long?,
        recurringId: Long?,
        recurringCycle: Int?,
        installmentId: Long?,
        installmentNumber: Int?,
        transactions: List<Transaction>,
    ): Operation = throw NotImplementedError()
    override suspend fun updateOperation(id: Long, transaction: Transaction) = throw NotImplementedError()
    override suspend fun deleteOperationById(id: Long) = throw NotImplementedError()
    override suspend fun deleteTransactionOperationsByCreditCard(creditCardId: Long) = throw NotImplementedError()
}

fun operation(
    id: Long = 1L,
    amount: Double = 100.0,
    type: Transaction.Type = Transaction.Type.EXPENSE,
): Operation = Operation(
    id = id,
    title = "Op $id",
    date = LocalDate(2026, 1, 1),
    transactions = listOf(
        Transaction(
            type = type,
            amount = amount,
            title = null,
            date = LocalDate(2026, 1, 1),
            target = Transaction.Target.ACCOUNT,
        ),
    ),
)
