package com.neoutils.finance.database.repository

import com.neoutils.finance.database.dao.TransactionDao
import com.neoutils.finance.database.mapper.TransactionMapper
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class TransactionRepository(
    private val dao: TransactionDao,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val mapper: TransactionMapper,
) : ITransactionRepository {

    override fun observeAllTransactions(): Flow<List<Transaction>> {
        return combine(
            dao.observeAllTransactions(),
            categoryRepository.observeAllCategories().map { categories ->
                categories.associateBy { it.id }
            },
            creditCardRepository.observeAllCreditCards().map { creditCards ->
                creditCards.associateBy { it.id }
            },
            invoiceRepository.observeAllInvoices().map { invoices ->
                invoices.associateBy { it.id }
            },
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

    override suspend fun getAllTransactions(): List<Transaction> {
        val transactions = dao.getAllTransactions()
        val categories = categoryRepository.getAllCategories().associateBy { it.id }
        val creditCards = creditCardRepository.getAllCreditCards().associateBy { it.id }
        val invoices = invoiceRepository.getAllInvoices().associateBy { it.id }

        return transactions.map { transaction ->
            mapper.toDomain(
                entity = transaction,
                category = categories[transaction.categoryId],
                creditCard = creditCards[transaction.creditCardId],
                invoice = invoices[transaction.invoiceId],
            )
        }
    }

    override suspend fun getTransactionById(id: Long): Transaction? {
        val transaction = dao.getTransactionById(id) ?: return null

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
            },
        )
    }

    override fun observeTransactionById(id: Long): Flow<Transaction?> {
        return combine(
            dao.observeTransactionById(id),
            categoryRepository.observeAllCategories().map { categories ->
                categories.associateBy { it.id }
            },
            creditCardRepository.observeAllCreditCards().map { creditCards ->
                creditCards.associateBy { it.id }
            },
            invoiceRepository.observeAllInvoices().map { invoices ->
                invoices.associateBy { it.id }
            },
        ) { transaction, categories, creditCards, invoices ->
            transaction?.let { transaction ->
                mapper.toDomain(
                    entity = transaction,
                    category = categories[transaction.categoryId],
                    creditCard = creditCards[transaction.creditCardId],
                    invoice = invoices[transaction.invoiceId],
                )
            }
        }
    }

    override fun observeTransactionsByType(type: Transaction.Type): Flow<List<Transaction>> {
        return combine(
            dao.observeTransactionsByType(mapper.toEntity(type)),
            categoryRepository.observeAllCategories().map { categories ->
                categories.associateBy { it.id }
            },
            creditCardRepository.observeAllCreditCards().map { creditCards ->
                creditCards.associateBy { it.id }
            },
            invoiceRepository.observeAllInvoices().map { invoices ->
                invoices.associateBy { it.id }
            },
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