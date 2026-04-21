package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface IOperationRepository {
    fun observeAllOperations(): Flow<List<Operation>>

    fun observeOperationsBy(
        date: LocalDate? = null,
        invoiceId: Long? = null,
        creditCardId: Long? = null,
        accountId: Long? = null,
    ): Flow<List<Operation>>

    suspend fun getAllOperations(): List<Operation>
    suspend fun getOperationById(id: Long): Operation?

    suspend fun createOperation(
        kind: Operation.Kind,
        title: String?,
        date: LocalDate,
        categoryId: Long?,
        sourceAccountId: Long?,
        targetCreditCardId: Long?,
        targetInvoiceId: Long?,
        recurringId: Long? = null,
        recurringCycle: Int? = null,
        installmentId: Long? = null,
        installmentNumber: Int? = null,
        transactions: List<Transaction>,
    ): Operation

    suspend fun updateOperation(id: Long, transaction: Transaction)

    suspend fun deleteOperationById(id: Long)
    suspend fun deleteTransactionOperationsByCreditCard(creditCardId: Long)
}
