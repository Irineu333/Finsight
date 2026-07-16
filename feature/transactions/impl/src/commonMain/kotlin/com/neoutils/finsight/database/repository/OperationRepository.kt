@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.database.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.dao.RecurringDao
import com.neoutils.finsight.database.dao.OperationDao
import com.neoutils.finsight.database.dao.TransactionDao
import com.neoutils.finsight.database.entity.OperationEntity
import com.neoutils.finsight.database.mapper.OperationMapper
import com.neoutils.finsight.database.mapper.RecurringMapper
import com.neoutils.finsight.database.mapper.TransactionMapper
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.extension.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine as flowCombine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class OperationRepository(
    private val database: AppDatabase,
    private val operationDao: OperationDao,
    private val transactionDao: TransactionDao,
    private val recurringDao: RecurringDao,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val installmentRepository: IInstallmentRepository,
    private val accountRepository: IAccountRepository,
    private val operationMapper: OperationMapper,
    private val recurringMapper: RecurringMapper,
    private val transactionMapper: TransactionMapper,
    private val ledgerEntryWriter: LedgerEntryWriter,
) : IOperationRepository {

    private val categoriesFlow = categoryRepository.observeAllCategories().map { it.associateBy { category -> category.id } }
    private val creditCardsFlow = creditCardRepository.observeAllCreditCards().map { it.associateBy { card -> card.id } }
    private val invoicesFlow = invoiceRepository.observeAllInvoices().map { it.associateBy { invoice -> invoice.id } }
    private val installmentsFlow = installmentRepository.observeAllInstallments().map { it.associateBy { installment -> installment.id } }
    private val accountsFlow = accountRepository.observeAllAccounts().map { it.associateBy { account -> account.id } }

    private val recurringFlow = flowCombine(
        recurringDao.observeAll(),
        categoriesFlow,
        accountsFlow,
        creditCardsFlow,
    ) { entities, categories, accounts, creditCards ->
        entities.associate { entity ->
            entity.id to recurringMapper.toDomain(
                entity = entity,
                category = entity.categoryId?.let { categories[it] },
                account = entity.accountId?.let { accounts[it] },
                creditCard = entity.creditCardId?.let { creditCards[it] },
            )
        }
    }

    override fun observeAllOperations(): Flow<List<Operation>> {
        return combine(
            operationDao.observeAll(),
            transactionDao.observeAllTransactionsRaw(),
            categoriesFlow,
            creditCardsFlow,
            invoicesFlow,
            installmentsFlow,
            accountsFlow,
            recurringFlow,
        ) { operations, transactions, categories, creditCards, invoices, installments, accounts, recurring ->
            val transactionsByOperationId = transactions.groupBy { it.operationId ?: 0L }
            operations.mapNotNull { operation ->
                val operationTransactions = transactionsByOperationId[operation.id].orEmpty()
                operationMapper.toDomain(
                    entity = operation,
                    transactions = operationTransactions.map { entity ->
                        transactionMapper.toDomain(
                            entity = entity,
                            category = categories[entity.categoryId],
                            creditCard = creditCards[entity.creditCardId],
                            invoice = invoices[entity.invoiceId],
                            account = accounts[entity.accountId],
                        )
                    },
                    categories = categories,
                    creditCards = creditCards,
                    invoices = invoices,
                    installments = installments,
                    accounts = accounts,
                    recurring = recurring,
                )
            }
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
            categoriesFlow,
            creditCardsFlow,
            invoicesFlow,
            installmentsFlow,
            accountsFlow,
            recurringFlow,
        ) { operations, transactions, categories, creditCards, invoices, installments, accounts, recurring ->
            val transactionsByOperationId = transactions.groupBy { it.operationId ?: 0L }
            operations.mapNotNull { operation ->
                val operationTransactions = transactionsByOperationId[operation.id].orEmpty()
                operationMapper.toDomain(
                    entity = operation,
                    transactions = operationTransactions.map { entity ->
                        transactionMapper.toDomain(
                            entity = entity,
                            category = categories[entity.categoryId],
                            creditCard = creditCards[entity.creditCardId],
                            invoice = invoices[entity.invoiceId],
                            account = accounts[entity.accountId],
                        )
                    },
                    categories = categories,
                    creditCards = creditCards,
                    invoices = invoices,
                    installments = installments,
                    accounts = accounts,
                    recurring = recurring,
                )
            }
        }
    }

    override fun observeOperationById(id: Long): Flow<Operation?> {
        return observeAllOperations()
            .map { operations -> operations.firstOrNull { it.id == id } }
            // Derived from the full list, so it re-runs on any operation/lookup change; only notify
            // consumers when the target actually changed.
            .distinctUntilChanged()
    }

    override suspend fun getAllOperations(): List<Operation> {
        val operations = operationDao.getAll()
        val categories = categoryRepository.getAllCategories().associateBy { it.id }
        val creditCards = creditCardRepository.getAllCreditCards().associateBy { it.id }
        val invoices = invoiceRepository.getAllInvoices().associateBy { it.id }
        val installments = installmentRepository.getAllInstallments().associateBy { it.id }
        val accounts = accountRepository.getAllAccounts().associateBy { it.id }
        val recurring = recurringDao.getAll()
            .associate { entity ->
                entity.id to recurringMapper.toDomain(
                    entity = entity,
                    category = entity.categoryId?.let { categories[it] },
                    account = entity.accountId?.let { accounts[it] },
                    creditCard = entity.creditCardId?.let { creditCards[it] },
                )
            }
        return operations.mapNotNull { operation ->
            val transactions = transactionDao
                .getTransactionsByOperationId(operation.id)
                .map { entity ->
                    transactionMapper.toDomain(
                        entity = entity,
                        category = categories[entity.categoryId],
                        creditCard = creditCards[entity.creditCardId],
                        invoice = invoices[entity.invoiceId],
                        account = accounts[entity.accountId],
                    )
                }
            operationMapper.toDomain(
                entity = operation,
                transactions = transactions,
                categories = categories,
                creditCards = creditCards,
                invoices = invoices,
                installments = installments,
                accounts = accounts,
                recurring = recurring,
            )
        }
    }

    override suspend fun getOperationById(id: Long): Operation? {
        val operation = operationDao.getById(id) ?: return null
        val categories = categoryRepository.getAllCategories().associateBy { it.id }
        val creditCards = creditCardRepository.getAllCreditCards().associateBy { it.id }
        val invoices = invoiceRepository.getAllInvoices().associateBy { it.id }
        val installments = installmentRepository.getAllInstallments().associateBy { it.id }
        val accounts = accountRepository.getAllAccounts().associateBy { it.id }
        val recurring = recurringDao.getAll()
            .associate { entity ->
                entity.id to recurringMapper.toDomain(
                    entity = entity,
                    category = entity.categoryId?.let { categories[it] },
                    account = entity.accountId?.let { accounts[it] },
                    creditCard = entity.creditCardId?.let { creditCards[it] },
                )
            }
        val transactions = transactionDao.getTransactionsByOperationId(id).map { entity ->
            transactionMapper.toDomain(
                entity = entity,
                category = categories[entity.categoryId],
                creditCard = creditCards[entity.creditCardId],
                invoice = invoices[entity.invoiceId],
                account = accounts[entity.accountId],
            )
        }
        return operationMapper.toDomain(
            entity = operation,
            transactions = transactions,
            categories = categories,
            creditCards = creditCards,
            invoices = invoices,
            installments = installments,
            accounts = accounts,
            recurring = recurring,
        )
    }

    override suspend fun createOperation(
        title: String?,
        date: LocalDate,
        categoryId: Long?,
        recurringId: Long?,
        recurringCycle: Int?,
        installmentId: Long?,
        installmentNumber: Int?,
        transactions: List<Transaction>,
    ): Operation {
        // Reject an unbalanced operation before writing anything (Σ = 0 per currency).
        ledgerEntryWriter.validate(transactions)

        // The operation, its legacy transactions and the ledger legs are written in a
        // single transaction so a mid-way failure (missing facade row, cancellation, DB
        // error) rolls back everything, never leaving an operation without its entries.
        val operationId = database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val operationId = operationDao.insert(
                    OperationEntity(
                        title = title,
                        date = date,
                        categoryId = categoryId,
                        recurringId = recurringId,
                        recurringCycle = recurringCycle,
                        installmentId = installmentId,
                        installmentNumber = installmentNumber,
                    )
                )

                val persisted = transactions.map { transaction ->
                    val transactionId = transactionDao.insert(
                        transactionMapper.toEntity(
                            transaction.copy(operationId = operationId)
                        )
                    )
                    transaction.copy(id = transactionId, operationId = operationId)
                }

                // Double-entry ledger legs, written alongside the legacy transactions.
                ledgerEntryWriter.writeEntries(operationId, persisted)

                operationId
            }
        }

        return getOperationById(operationId)!!
    }

    override suspend fun updateOperation(id: Long, transaction: Transaction) {
        // Update and ledger rewrite (delete + re-insert legs) share one transaction, so a
        // failure never leaves the operation with its old legs deleted and no new ones.
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                operationDao.update(
                    id = id,
                    title = transaction.title,
                    date = transaction.date,
                    categoryId = transaction.category?.id,
                )
                // Keep the ledger consistent with the edited (single) leg.
                ledgerEntryWriter.rewriteEntries(id, listOf(transaction))
            }
        }
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
