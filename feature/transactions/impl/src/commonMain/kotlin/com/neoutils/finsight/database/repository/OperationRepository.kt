@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.database.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.dao.RecurringDao
import com.neoutils.finsight.database.dao.TransactionDao
import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.mapper.OperationMapper
import com.neoutils.finsight.database.mapper.RecurringMapper
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.OperationIntent
import com.neoutils.finsight.domain.model.OperationLeg
import com.neoutils.finsight.domain.model.Recurring
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
    private val transactionDao: TransactionDao,
    private val entryDao: EntryDao,
    private val recurringDao: RecurringDao,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val installmentRepository: IInstallmentRepository,
    private val accountRepository: IAccountRepository,
    private val operationMapper: OperationMapper,
    private val recurringMapper: RecurringMapper,
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

    private fun List<EntryEntity>.toDomainEntries(accounts: Map<Long, Account>): List<Entry> =
        mapNotNull { entity ->
            accounts[entity.accountId]?.let { account ->
                Entry(
                    id = entity.id,
                    transactionId = entity.transactionId,
                    account = account,
                    amount = entity.amount,
                    currency = entity.currency,
                    invoiceId = entity.invoiceId,
                )
            }
        }

    private fun Flow<List<TransactionEntity>>.mapToDomain(): Flow<List<Operation>> = combine(
        this,
        categoriesFlow,
        creditCardsFlow,
        invoicesFlow,
        installmentsFlow,
        accountsFlow,
        recurringFlow,
        entryDao.observeAll(),
    ) { operations, categories, creditCards, invoices, installments, accounts, recurring, entries ->
        val entriesByOperationId = entries.groupBy { it.transactionId }
        operations.mapNotNull { operation ->
            operationMapper.toDomain(
                entity = operation,
                categories = categories,
                creditCards = creditCards,
                invoices = invoices,
                installments = installments,
                recurring = recurring,
                entries = entriesByOperationId[operation.id].orEmpty().toDomainEntries(accounts),
            )
        }
    }

    override fun observeAllOperations(): Flow<List<Operation>> =
        transactionDao.observeAll().mapToDomain()

    override fun observeOperationsBy(
        date: LocalDate?,
        invoiceId: Long?,
        creditCardId: Long?,
        accountId: Long?,
    ): Flow<List<Operation>> = transactionDao.observeBy(
        date = date,
        invoiceId = invoiceId,
        creditCardId = creditCardId,
        accountId = accountId,
    ).mapToDomain()

    override fun observeOperationById(id: Long): Flow<Operation?> {
        return observeAllOperations()
            .map { operations -> operations.firstOrNull { it.id == id } }
            // Derived from the full list, so it re-runs on any operation/lookup change; only notify
            // consumers when the target actually changed.
            .distinctUntilChanged()
    }

    private fun OperationIntent.toEntity() = TransactionEntity(
        title = title,
        date = date,
        categoryId = category?.id,
        recurringId = recurringId,
        recurringCycle = recurringCycle,
        installmentId = installmentId,
        installmentNumber = installmentNumber,
    )

    private data class Lookups(
        val categories: Map<Long, Category>,
        val creditCards: Map<Long, CreditCard>,
        val invoices: Map<Long, Invoice>,
        val installments: Map<Long, Installment>,
        val accounts: Map<Long, Account>,
        val recurring: Map<Long, Recurring>,
    )

    private suspend fun lookups(): Lookups {
        val categories = categoryRepository.getAllCategories().associateBy { it.id }
        val creditCards = creditCardRepository.getAllCreditCards().associateBy { it.id }
        val accounts = accountRepository.getAllAccounts().associateBy { it.id }
        return Lookups(
            categories = categories,
            creditCards = creditCards,
            invoices = invoiceRepository.getAllInvoices().associateBy { it.id },
            installments = installmentRepository.getAllInstallments().associateBy { it.id },
            accounts = accounts,
            recurring = recurringDao.getAll().associate { entity ->
                entity.id to recurringMapper.toDomain(
                    entity = entity,
                    category = entity.categoryId?.let { categories[it] },
                    account = entity.accountId?.let { accounts[it] },
                    creditCard = entity.creditCardId?.let { creditCards[it] },
                )
            },
        )
    }

    private suspend fun TransactionEntity.toDomain(lookups: Lookups): Operation? = operationMapper.toDomain(
        entity = this,
        categories = lookups.categories,
        creditCards = lookups.creditCards,
        invoices = lookups.invoices,
        installments = lookups.installments,
        recurring = lookups.recurring,
        entries = entryDao.getByOperationId(id).toDomainEntries(lookups.accounts),
    )

    override suspend fun getAllOperations(): List<Operation> {
        val lookups = lookups()
        return transactionDao.getAll().mapNotNull { it.toDomain(lookups) }
    }

    override suspend fun getOperationById(id: Long): Operation? {
        val entity = transactionDao.getById(id) ?: return null
        return entity.toDomain(lookups())
    }

    override suspend fun createOperation(intent: OperationIntent): Operation {
        // Reject an unbalanced intent before writing anything (Σ = 0 per currency).
        ledgerEntryWriter.validate(intent.legs)

        // The operation row and its ledger legs are written in a single transaction,
        // so a mid-way failure (missing facade row, cancellation, DB error) rolls back
        // everything, never leaving an operation without its entries.
        val transactionId = database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val transactionId = transactionDao.insert(intent.toEntity())

                ledgerEntryWriter.writeEntries(transactionId, intent.legs)

                transactionId
            }
        }

        return getOperationById(transactionId)!!
    }

    override suspend fun createOperations(intents: List<OperationIntent>): List<Operation> {
        intents.forEach { ledgerEntryWriter.validate(it.legs) }

        val ids = database.useWriterConnection { connection ->
            connection.immediateTransaction {
                intents.map { intent ->
                    val operationId = transactionDao.insert(intent.toEntity())
                    ledgerEntryWriter.writeEntries(operationId, intent.legs)
                    operationId
                }
            }
        }

        val lookups = lookups()
        return ids.mapNotNull { transactionDao.getById(it)?.toDomain(lookups) }
    }

    override suspend fun updateOperation(id: Long, title: String?, date: LocalDate, leg: OperationLeg) {
        // Update and ledger rewrite (delete + re-insert legs) share one transaction, so a
        // failure never leaves the operation with its old legs deleted and no new ones.
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                transactionDao.update(
                    id = id,
                    title = title,
                    date = date,
                    categoryId = leg.category?.id,
                )
                ledgerEntryWriter.rewriteEntries(id, listOf(leg))
            }
        }
    }

    override suspend fun deleteOperationById(id: Long) {
        // The installment bookkeeping and the row removal are one transaction: a
        // failure between them would leave the installment's count and total
        // describing operations that no longer exist.
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val operation = transactionDao.getById(id)
                val installmentId = operation?.installmentId

                if (installmentId == null) {
                    transactionDao.deleteById(id)
                    return@immediateTransaction
                }

                // The operation's own share of the installment, from the ledger.
                val operationAmount = entryDao.getByOperationId(id)
                    .filter { it.amount < 0 }
                    .sumOf { -it.amount } / 100.0
                val remainingCount = transactionDao.countByInstallmentId(installmentId) - 1

                transactionDao.deleteById(id)

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
            }
        }
    }

    override suspend fun deleteTransactionOperationsByCreditCard(creditCardId: Long) {
        transactionDao.deleteTransactionsByCreditCardId(creditCardId)
    }
}
