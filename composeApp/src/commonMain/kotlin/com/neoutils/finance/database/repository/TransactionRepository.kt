@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finance.database.repository

import com.neoutils.finance.database.dao.TransactionDao
import com.neoutils.finance.database.mapper.TransactionMapper
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class TransactionRepository(
    private val dao: TransactionDao,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
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

    override fun observeAllTransactions(): Flow<List<Transaction>> {
        return combine(
            dao.observeAllTransactions(),
            categoriesFlow,
            creditCardsFlow,
            invoicesFlow,
        ) { entities, categories, creditCards, invoices ->
            entities.map { entity ->
                mapper.toDomain(
                    entity = entity,
                    category = categories[entity.categoryId],
                    creditCard = creditCards[entity.creditCardId],
                    invoice = invoices[entity.invoiceId],
                )
            }
        }
    }

    override suspend fun getAllTransactions(): List<Transaction> {
        val entities = dao.getAllTransactions()
        val categories = categoryRepository.getAllCategories().associateBy { it.id }
        val creditCards = creditCardRepository.getAllCreditCards().associateBy { it.id }
        val invoices = invoiceRepository.getAllInvoices().associateBy { it.id }

        return entities.map { entity ->
            mapper.toDomain(
                entity = entity,
                category = categories[entity.categoryId],
                creditCard = creditCards[entity.creditCardId],
                invoice = invoices[entity.invoiceId],
            )
        }
    }

    override suspend fun getTransactionById(id: Long): Transaction? {
        val entity = dao.getTransactionById(id) ?: return null

        return mapper.toDomain(
            entity = entity,
            category = entity.categoryId?.let {
                categoryRepository.getCategoryById(it)
            },
            creditCard = entity.creditCardId?.let {
                creditCardRepository.getCreditCardById(it)
            },
            invoice = entity.invoiceId?.let {
                invoiceRepository.getInvoiceById(it)
            },
        )
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

            combine(
                categoryFlow ?: flowOf(null),
                creditCardFlow ?: flowOf(null),
                invoiceFlow ?: flowOf(null),
            ) { category, creditCard, invoice ->
                mapper.toDomain(
                    entity = entity,
                    category = category,
                    creditCard = creditCard,
                    invoice = invoice,
                )
            }
        }
    }

    override fun observeTransactionsByType(type: Transaction.Type): Flow<List<Transaction>> {
        return combine(
            dao.observeTransactionsByType(mapper.toEntity(type)),
            categoriesFlow,
            creditCardsFlow,
            invoicesFlow,
        ) { transactions, categories, creditCards, invoices ->
            transactions.map { transaction ->
                mapper.toDomain(
                    entity = transaction,
                    category = categories[transaction.categoryId],
                    creditCard = creditCards[transaction.creditCardId],
                    invoice = invoices[transaction.invoiceId],
                )
            }
        }
    }

    override suspend fun getTransactionByTypeAndDate(
        type: Transaction.Type,
        date: LocalDate
    ): Transaction? {
        val transaction = dao.getTransactionByTypeAndDate(mapper.toEntity(type), date) ?: return null

        return mapper.toDomain(
            entity = transaction,
            category = transaction.categoryId?.let {
                categoryRepository.getCategoryById(it)
            },
            creditCard = transaction.creditCardId?.let {
                creditCardRepository.getCreditCardById(it)
            },
            invoice = transaction.invoiceId?.let {
                invoiceRepository.getInvoiceById(it)
            }
        )
    }

    override suspend fun getTransactionsBy(
        type: Transaction.Type?,
        target: Transaction.Target?,
        date: LocalDate?,
        invoiceId: Long?
    ): List<Transaction> {

        val transactions = dao.getTransactionsBy(
            type = type?.let { mapper.toEntity(it) },
            target = target?.let { mapper.toEntity(it) },
            date = date,
            invoiceId = invoiceId,
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
            )
        }
    }

    override fun observeTransactionsBy(
        type: Transaction.Type?,
        target: Transaction.Target?,
        date: LocalDate?,
        invoiceId: Long?,
        creditCardId: Long?,
    ): Flow<List<Transaction>> {
        return combine(
            dao.observeTransactionsBy(
                type = type?.let { mapper.toEntity(it) },
                target = target?.let { mapper.toEntity(it) },
                date = date,
                invoiceId = invoiceId,
                creditCardId = creditCardId,
            ),
            categoriesFlow,
            creditCardsFlow,
            invoicesFlow,
        ) { transactions, categories, creditCards, invoices ->
            transactions.map { transaction ->
                mapper.toDomain(
                    entity = transaction,
                    category = categories[transaction.categoryId],
                    creditCard = creditCards[transaction.creditCardId],
                    invoice = invoices[transaction.invoiceId],
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

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}