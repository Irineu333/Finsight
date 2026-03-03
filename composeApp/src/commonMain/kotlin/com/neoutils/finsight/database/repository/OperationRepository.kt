@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.OperationDao
import com.neoutils.finsight.database.dao.TransactionDao
import com.neoutils.finsight.database.entity.OperationEntity
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.database.mapper.TransactionMapper
import com.neoutils.finsight.domain.model.Operation
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
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class OperationRepository(
    private val operationDao: OperationDao,
    private val transactionDao: TransactionDao,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val installmentRepository: IInstallmentRepository,
    private val accountRepository: IAccountRepository,
    private val transactionMapper: TransactionMapper,
) : IOperationRepository {

    private val categoriesFlow = categoryRepository.observeAllCategories().map { it.associateBy { category -> category.id } }
    private val creditCardsFlow = creditCardRepository.observeAllCreditCards().map { it.associateBy { card -> card.id } }
    private val invoicesFlow = invoiceRepository.observeAllInvoices().map { it.associateBy { invoice -> invoice.id } }
    private val installmentsFlow = installmentRepository.observeAllInstallments().map { it.associateBy { installment -> installment.id } }
    private val accountsFlow = accountRepository.observeAllAccounts().map { it.associateBy { account -> account.id } }

    override fun observeAllOperations(): Flow<List<Operation>> {
        return combine(
            operationDao.observeAll(),
            transactionDao.observeAllTransactionsRaw(),
            categoriesFlow,
            creditCardsFlow,
            invoicesFlow,
            installmentsFlow,
            accountsFlow,
        ) { operations, transactions, categories, creditCards, invoices, installments, accounts ->
            val transactionsByOperationId = transactions.groupBy { it.operationId ?: 0L }
            operations.mapNotNull { operation ->
                val operationTransactions = transactionsByOperationId[operation.id].orEmpty()
                toDomain(
                    operation = operation,
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
        ) { operations, transactions, categories, creditCards, invoices, installments, accounts ->
            val transactionsByOperationId = transactions.groupBy { it.operationId ?: 0L }
            operations.mapNotNull { operation ->
                val operationTransactions = transactionsByOperationId[operation.id].orEmpty()
                toDomain(
                    operation = operation,
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
                )
            }
        }
    }

    override suspend fun getAllOperations(): List<Operation> {
        val operations = operationDao.getAll()
        val categories = categoryRepository.getAllCategories().associateBy { it.id }
        val creditCards = creditCardRepository.getAllCreditCards().associateBy { it.id }
        val invoices = invoiceRepository.getAllInvoices().associateBy { it.id }
        val installments = installmentRepository.getAllInstallments().associateBy { it.id }
        val accounts = accountRepository.getAllAccounts().associateBy { it.id }
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
            toDomain(
                operation = operation,
                transactions = transactions,
                categories = categories,
                creditCards = creditCards,
                invoices = invoices,
                installments = installments,
                accounts = accounts,
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
        val transactions = transactionDao.getTransactionsByOperationId(id).map { entity ->
            transactionMapper.toDomain(
                entity = entity,
                category = categories[entity.categoryId],
                creditCard = creditCards[entity.creditCardId],
                invoice = invoices[entity.invoiceId],
                account = accounts[entity.accountId],
            )
        }
        return toDomain(
            operation = operation,
            transactions = transactions,
            categories = categories,
            creditCards = creditCards,
            invoices = invoices,
            installments = installments,
            accounts = accounts,
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
                kind = toEntity(kind),
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
            categoryId = transaction.category?.id,
            sourceAccountId = transaction.account?.id,
            targetCreditCardId = transaction.creditCard?.id,
            targetInvoiceId = transaction.invoice?.id,
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

    private fun toDomain(
        operation: OperationEntity,
        transactions: List<Transaction>,
        categories: Map<Long, com.neoutils.finsight.domain.model.Category>,
        creditCards: Map<Long, com.neoutils.finsight.domain.model.CreditCard>,
        invoices: Map<Long, com.neoutils.finsight.domain.model.Invoice>,
        installments: Map<Long, Installment>,
        accounts: Map<Long, com.neoutils.finsight.domain.model.Account>,
    ): Operation? {
        if (transactions.isEmpty()) return null
        val primaryTransaction = transactions
            .firstOrNull { it.target == Transaction.Target.ACCOUNT }
            ?: transactions.first()

        return Operation(
            id = operation.id,
            kind = toDomain(operation.kind),
            title = operation.title ?: primaryTransaction.title,
            date = primaryTransaction.date,
            recurring = operation.recurringId?.let { recurringId ->
                operation.recurringCycle?.let { cycleNumber ->
                    Operation.Recurring(
                        id = recurringId,
                        cycleNumber = cycleNumber,
                    )
                }
            },
            category = operation.categoryId?.let { categories[it] } ?: primaryTransaction.category,
            sourceAccount = operation.sourceAccountId?.let { accounts[it] },
            targetCreditCard = operation.targetCreditCardId?.let { creditCards[it] },
            targetInvoice = operation.targetInvoiceId?.let { invoices[it] },
            installment = operation.installmentNumber?.let { number ->
                operation.installmentId?.let { installmentId ->
                    installments[installmentId]?.copy(number = number)
                }
            },
            transactions = transactions.sortedByDescending { it.id },
        )
    }

    private fun toDomain(kind: OperationEntity.Kind): Operation.Kind {
        return when (kind) {
            OperationEntity.Kind.TRANSACTION -> Operation.Kind.TRANSACTION
            OperationEntity.Kind.PAYMENT -> Operation.Kind.PAYMENT
            OperationEntity.Kind.TRANSFER -> Operation.Kind.TRANSFER
        }
    }

    private fun toEntity(kind: Operation.Kind): OperationEntity.Kind {
        return when (kind) {
            Operation.Kind.TRANSACTION -> OperationEntity.Kind.TRANSACTION
            Operation.Kind.PAYMENT -> OperationEntity.Kind.PAYMENT
            Operation.Kind.TRANSFER -> OperationEntity.Kind.TRANSFER
        }
    }
}
