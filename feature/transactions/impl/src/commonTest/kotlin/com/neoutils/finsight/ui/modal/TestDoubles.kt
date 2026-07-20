package com.neoutils.finsight.ui.modal

import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.OperationIntent
import com.neoutils.finsight.domain.model.OperationLeg
import com.neoutils.finsight.domain.model.TransactionType
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
    override suspend fun createOperation(intent: OperationIntent): Operation = throw NotImplementedError()
    override suspend fun createOperations(intents: List<OperationIntent>): List<Operation> = throw NotImplementedError()
    override suspend fun updateOperation(id: Long, title: String?, date: LocalDate, leg: OperationLeg) = throw NotImplementedError()
    override suspend fun deleteOperationById(id: Long) = throw NotImplementedError()
    override suspend fun deleteTransactionOperationsByCreditCard(creditCardId: Long) = throw NotImplementedError()
}

/**
 * An operation as the ledger holds it: the money leg on an asset account plus the
 * counterpart leg that explains it — an EQUITY reconciliation for an adjustment.
 */
fun operation(
    id: Long = 1L,
    amount: Double = 100.0,
    type: TransactionType = TransactionType.EXPENSE,
): Operation {
    val cents = (amount * 100).toLong()
    val (moneyAmount, counterpart) = when (type) {
        TransactionType.EXPENSE -> -cents to Account(id = 10, name = "Food", type = AccountType.EXPENSE)
        TransactionType.INCOME -> cents to Account(id = 11, name = "Salary", type = AccountType.INCOME)
        TransactionType.ADJUSTMENT -> cents to Account(id = 12, name = "Reconciliation", type = AccountType.EQUITY)
    }

    return Operation(
        id = id,
        title = "Op $id",
        date = LocalDate(2026, 1, 1),
        entries = listOf(
            Entry(account = Account(id = 1, name = "Account", type = AccountType.ASSET), amount = moneyAmount),
            Entry(account = counterpart, amount = -moneyAmount),
        ),
    )
}
