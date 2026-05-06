@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.feature.transactions.repository

import com.neoutils.finsight.core.database.dao.OperationDao
import com.neoutils.finsight.core.database.dao.RecurringDao
import com.neoutils.finsight.core.database.dao.TransactionDao
import com.neoutils.finsight.core.database.entity.OperationEntity
import com.neoutils.finsight.core.database.entity.TransactionEntity
import com.neoutils.finsight.feature.installments.model.Installment
import com.neoutils.finsight.feature.installments.repository.IInstallmentRepository
import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.recurring.mapper.IRecurringMapper
import com.neoutils.finsight.feature.transactions.mapper.OperationMapper
import com.neoutils.finsight.feature.transactions.mapper.TransactionMapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class OperationRepository(
    private val operationDao: OperationDao,
    private val transactionDao: TransactionDao,
    private val recurringDao: RecurringDao,
    private val installmentRepository: IInstallmentRepository,
    private val operationMapper: OperationMapper,
    private val recurringMapper: IRecurringMapper,
    private val transactionMapper: TransactionMapper,
) : IOperationRepository {

    private val installmentsFlow = installmentRepository.observeAllInstallments().map { it.associateBy { i -> i.id } }

    private val recurringFlow = recurringDao.observeAll().map { entities ->
        entities.associate { entity ->
            entity.id to recurringMapper.toDomain(entity)
        }
    }

    private fun assemble(
        operations: List<OperationEntity>,
        transactions: List<TransactionEntity>,
        installments: Map<Long, Installment>,
        recurring: Map<Long, Recurring>,
    ): List<Operation> {
        val transactionsByOperationId = transactions.groupBy { it.operationId ?: 0L }
        return operations.mapNotNull { operation ->
            val operationTransactions = transactionsByOperationId[operation.id]
                .orEmpty()
                .map(transactionMapper::toDomain)
            operationMapper.toDomain(
                entity = operation,
                transactions = operationTransactions,
                installments = installments,
                recurring = recurring,
            )
        }
    }

    override fun observeAllOperations(): Flow<List<Operation>> {
        return combine(
            operationDao.observeAll(),
            transactionDao.observeAllTransactionsRaw(),
            installmentsFlow,
            recurringFlow,
        ) { operations, transactions, installments, recurring ->
            assemble(operations, transactions, installments, recurring)
        }
    }

    override fun observeOperationsBy(
        date: LocalDate?,
        invoiceId: Long?,
        creditCardId: Long?,
        accountId: Long?,
    ): Flow<List<Operation>> {
        return combine(
            operationDao.observeBy(
                date = date,
                invoiceId = invoiceId,
                creditCardId = creditCardId,
                accountId = accountId,
            ),
            transactionDao.observeAllTransactionsRaw(),
            installmentsFlow,
            recurringFlow,
        ) { operations, transactions, installments, recurring ->
            assemble(operations, transactions, installments, recurring)
        }
    }

    override suspend fun getAllOperations(): List<Operation> {
        val operations = operationDao.getAll()
        val installments = installmentRepository.getAllInstallments().associateBy { it.id }
        val recurring = recurringDao.getAll().associate { entity ->
            entity.id to recurringMapper.toDomain(entity)
        }
        return operations.mapNotNull { operation ->
            val transactions = transactionDao
                .getTransactionsByOperationId(operation.id)
                .map(transactionMapper::toDomain)
            operationMapper.toDomain(
                entity = operation,
                transactions = transactions,
                installments = installments,
                recurring = recurring,
            )
        }
    }

    override suspend fun getOperationById(id: Long): Operation? {
        val operation = operationDao.getById(id) ?: return null
        val installments = installmentRepository.getAllInstallments().associateBy { it.id }
        val recurring = recurringDao.getAll().associate { entity ->
            entity.id to recurringMapper.toDomain(entity)
        }
        val transactions = transactionDao.getTransactionsByOperationId(id).map(transactionMapper::toDomain)
        return operationMapper.toDomain(
            entity = operation,
            transactions = transactions,
            installments = installments,
            recurring = recurring,
        )
    }

    override suspend fun createOperation(
        kind: Operation.Kind,
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
    ): Operation {
        val operationId = operationDao.insert(
            OperationEntity(
                kind = operationMapper.toEntity(kind),
                title = title,
                date = date,
                categoryId = categoryId,
                sourceAccountId = sourceAccountId,
                targetCreditCardId = targetCreditCardId,
                targetInvoiceId = targetInvoiceId,
                recurringId = recurringId,
                recurringCycle = recurringCycle,
                installmentId = installmentId,
                installmentNumber = installmentNumber,
            )
        )

        transactions.forEach { transaction ->
            transactionDao.insert(
                transactionMapper.toEntity(
                    transaction.copy(operationId = operationId)
                )
            )
        }

        return getOperationById(operationId)!!
    }

    override suspend fun updateOperation(id: Long, transaction: Transaction) {
        operationDao.update(
            id = id,
            title = transaction.title,
            date = transaction.date,
            categoryId = transaction.categoryId,
            sourceAccountId = transaction.accountId,
            targetCreditCardId = transaction.creditCardId,
            targetInvoiceId = transaction.invoiceId,
        )
    }

    override suspend fun deleteOperationById(id: Long) {
        val operation = operationDao.getById(id)
        val installmentId = operation?.installmentId

        if (installmentId != null) {
            val transactions = transactionDao.getTransactionsByOperationId(id)
            val operationAmount = transactions.sumOf { it.amount }
            val remainingCount = operationDao.countByInstallmentId(installmentId) - 1

            transactionDao.deleteByOperationId(id)
            operationDao.deleteById(id)

            if (remainingCount <= 0) {
                installmentRepository.deleteInstallmentById(installmentId)
            } else {
                val installment = installmentRepository.getInstallmentById(installmentId)
                if (installment != null) {
                    installmentRepository.updateInstallment(
                        id = installmentId,
                        count = remainingCount,
                        totalAmount = installment.totalAmount - operationAmount,
                    )
                }
            }
        } else {
            transactionDao.deleteByOperationId(id)
            operationDao.deleteById(id)
        }
    }

    override suspend fun deleteTransactionOperationsByCreditCard(creditCardId: Long) {
        operationDao.deleteTransactionsByCreditCardId(creditCardId)
    }
}
