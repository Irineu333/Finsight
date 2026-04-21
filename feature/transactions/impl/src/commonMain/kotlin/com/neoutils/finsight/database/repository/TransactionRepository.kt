@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.TransactionDao
import com.neoutils.finsight.database.mapper.TransactionMapper
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class TransactionRepository(
    private val dao: TransactionDao,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val accountRepository: IAccountRepository,
    private val mapper: TransactionMapper,
) : ITransactionRepository {

    private val categoriesFlow = categoryRepository
        .observeAllCategories()
        .map { categories ->
            categories.associateBy { it.id }
        }

    private val creditCardsFlow = creditCardRepository
        .observeAllCreditCards()
        .map { creditCards ->
            creditCards.associateBy { it.id }
        }

    private val invoicesFlow = invoiceRepository
        .observeAllInvoices()
        .map { invoices ->
            invoices.associateBy { it.id }
        }

    private val accountsFlow = accountRepository
        .observeAllAccounts()
        .map { accounts ->
            accounts.associateBy { it.id }
        }

    override fun observeAllTransactions(): Flow<List<Transaction>> {
        return combine(
            dao.observeAllTransactions(),
            categoriesFlow,
            creditCardsFlow,
            invoicesFlow,
            accountsFlow,
        ) { entities, categories, creditCards, invoices, accounts ->
            entities.map { entity ->
                mapper.toDomain(
                    entity = entity,
                    category = categories[entity.categoryId],
                    creditCard = creditCards[entity.creditCardId],
                    invoice = invoices[entity.invoiceId],
                    account = accounts[entity.accountId],
                )
            }
        }
    }

    override suspend fun getAllTransactions(): List<Transaction> {
        val entities = dao.getAllTransactions()
        val categories = categoryRepository.getAllCategories().associateBy { it.id }
        val creditCards = creditCardRepository.getAllCreditCards().associateBy { it.id }
        val invoices = invoiceRepository.getAllInvoices().associateBy { it.id }
        val accounts = accountRepository.getAllAccounts().associateBy { it.id }

        return entities.map { entity ->
            mapper.toDomain(
                entity = entity,
                category = categories[entity.categoryId],
                creditCard = creditCards[entity.creditCardId],
                invoice = invoices[entity.invoiceId],
                account = accounts[entity.accountId],
            )
        }
    }

    override fun observeTransactionById(id: Long): Flow<Transaction?> {
        return dao.observeTransactionById(id).flatMapMerge { entity ->
            if (entity == null) {
                return@flatMapMerge emptyFlow()
            }

            val categoryFlow = entity.categoryId?.let { categoryId ->
                categoryRepository.observeCategoryById(categoryId)
            }

            val creditCardFlow = entity.creditCardId?.let { creditCardId ->
                creditCardRepository.observeCreditCardById(creditCardId)
            }

            val invoiceFlow = entity.invoiceId?.let { invoiceId ->
                invoiceRepository.observeInvoiceById(invoiceId)
            }

            val accountFlow = entity.accountId?.let { accountId ->
                accountRepository.observeAccountById(accountId)
            }

            combine(
                categoryFlow ?: flowOf(null),
                creditCardFlow ?: flowOf(null),
                invoiceFlow ?: flowOf(null),
                accountFlow ?: flowOf(null),
            ) { category, creditCard, invoice, account ->
                mapper.toDomain(
                    entity = entity,
                    category = category,
                    creditCard = creditCard,
                    invoice = invoice,
                    account = account,
                )
            }
        }
    }

    override suspend fun getTransactionBy(id: Long): Transaction? {
        val entity = dao.getTransactionById(id) ?: return null

        return mapper.toDomain(
            entity = entity,
            category = entity.categoryId?.let { categoryRepository.getCategoryById(it) },
            creditCard = entity.creditCardId?.let { creditCardRepository.getCreditCardById(it) },
            invoice = entity.invoiceId?.let { invoiceRepository.getInvoiceById(it) },
            account = entity.accountId?.let { accountRepository.getAccountById(it) },
        )
    }

    override suspend fun getTransactionsBy(
        type: Transaction.Type?,
        target: Transaction.Target?,
        date: LocalDate?,
        invoiceId: Long?,
        accountId: Long?,
    ): List<Transaction> {

        val transactions = dao.getTransactionsBy(
            type = type?.let { mapper.toEntity(it) },
            target = target?.let { mapper.toEntity(it) },
            date = date,
            invoiceId = invoiceId,
            accountId = accountId,
        )

        return transactions.map { transaction ->
            mapper.toDomain(
                entity = transaction,
                category = transaction.categoryId?.let {
                    categoryRepository.getCategoryById(it)
                },
                creditCard = transaction.creditCardId?.let {
                    creditCardRepository.getCreditCardById(it)
                },
                invoice = transaction.invoiceId?.let {
                    invoiceRepository.getInvoiceById(it)
                },
                account = transaction.accountId?.let {
                    accountRepository.getAccountById(it)
                },
            )
        }
    }

    override fun observeTransactionsBy(
        type: Transaction.Type?,
        target: Transaction.Target?,
        date: LocalDate?,
        invoiceId: Long?,
        creditCardId: Long?,
        accountId: Long?,
    ): Flow<List<Transaction>> {
        return combine(
            dao.observeTransactionsBy(
                type = type?.let { mapper.toEntity(it) },
                target = target?.let { mapper.toEntity(it) },
                date = date,
                invoiceId = invoiceId,
                creditCardId = creditCardId,
                accountId = accountId,
            ),
            categoriesFlow,
            creditCardsFlow,
            invoicesFlow,
            accountsFlow,
        ) { transactions, categories, creditCards, invoices, accounts ->
            transactions.map { transaction ->
                mapper.toDomain(
                    entity = transaction,
                    category = categories[transaction.categoryId],
                    creditCard = creditCards[transaction.creditCardId],
                    invoice = invoices[transaction.invoiceId],
                    account = accounts[transaction.accountId],
                )
            }
        }
    }

    override suspend fun insert(transaction: Transaction): Long {
        return dao.insert(mapper.toEntity(transaction))
    }

    override suspend fun update(transaction: Transaction) {
        dao.update(mapper.toEntity(transaction))
    }

    override suspend fun delete(transaction: Transaction) {
        dao.delete(mapper.toEntity(transaction))
    }
}