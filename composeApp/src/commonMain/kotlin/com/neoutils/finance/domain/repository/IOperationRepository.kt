package com.neoutils.finance.domain.repository

import com.neoutils.finance.domain.model.Operation
import com.neoutils.finance.domain.model.Transaction
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
        transactions: List<Transaction>,
    ): Operation

    suspend fun deleteOperationById(id: Long)
}
