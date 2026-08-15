package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * The ledger's write surface with everything refused, so a suite overrides only the
 * one method it is about — and so a call it did not expect fails loudly instead of
 * quietly answering nothing.
 */
internal abstract class NotUnderTestTransactions : ITransactionRepository {
    override fun observeAllTransactions(): Flow<List<Transaction>> = throw NotImplementedError()
    override fun observeTransactionsBy(
        date: LocalDate?,
        dimensionId: Long?,
        accountId: Long?,
    ): Flow<List<Transaction>> = throw NotImplementedError()

    override suspend fun getTransactionsBy(
        startDate: LocalDate?,
        endDate: LocalDate?,
        dimensionId: Long?,
        accountId: Long?,
    ): List<Transaction> = throw NotImplementedError()

    override fun observeTransactionById(id: Long): Flow<Transaction?> = throw NotImplementedError()
    override suspend fun getAllTransactions(): List<Transaction> = throw NotImplementedError()
    override suspend fun getTransactionById(id: Long): Transaction? = throw NotImplementedError()
    override suspend fun createTransaction(intent: TransactionIntent): Transaction = throw NotImplementedError()
    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> = throw NotImplementedError()
    override suspend fun updateTransaction(
        id: Long,
        title: String?,
        date: LocalDate,
        leg: TransactionLeg,
        contra: ContraLeg?,
    ): Unit = throw NotImplementedError()

    override suspend fun deleteTransactionById(id: Long): Unit = throw NotImplementedError()
    override suspend fun deleteTransactionsByIds(ids: List<Long>): Unit = throw NotImplementedError()
}
