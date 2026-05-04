package com.neoutils.finsight.feature.transactions.mapper

import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.OperationPerspective
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.accounts.repository.IAccountRepository
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import com.neoutils.finsight.feature.creditCards.mapper.IInvoiceUiMapper
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.creditCards.repository.IInvoiceRepository
import com.neoutils.finsight.feature.transactions.model.OperationUi
import com.neoutils.finsight.feature.transactions.model.TransactionUi

class OperationUiMapper(
    private val accountRepository: IAccountRepository,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val invoiceUiMapper: IInvoiceUiMapper,
) : IOperationUiMapper {

    override suspend fun toUi(transaction: Transaction): TransactionUi {
        val account = transaction.accountId?.let { accountRepository.getAccountById(it) }
        val category = transaction.categoryId?.let { categoryRepository.getCategoryById(it) }
        val creditCard = transaction.creditCardId?.let { creditCardRepository.getCreditCardById(it) }
        val invoice = transaction.invoiceId?.let { invoiceRepository.getInvoiceById(it) }
        val invoiceUi = invoice?.let { invoiceUiMapper.toUi(it) }
        return TransactionUi(
            transaction = transaction,
            account = account,
            category = category,
            creditCard = creditCard,
            invoice = invoiceUi,
        )
    }

    override suspend fun toUi(
        operation: Operation,
        perspective: OperationPerspective,
    ): OperationUi {
        val category = operation.categoryId?.let { categoryRepository.getCategoryById(it) }
        val sourceAccount = operation.sourceAccountId?.let { accountRepository.getAccountById(it) }
        val targetCreditCard = operation.targetCreditCardId?.let { creditCardRepository.getCreditCardById(it) }
        val targetInvoice = operation.targetInvoiceId?.let { invoiceRepository.getInvoiceById(it) }
        val targetInvoiceUi = targetInvoice?.let { invoiceUiMapper.toUi(it) }
        val transactionsUi = operation.transactions.map { toUi(it) }
        return OperationUi(
            operation = operation,
            perspective = perspective,
            category = category,
            sourceAccount = sourceAccount,
            targetCreditCard = targetCreditCard,
            targetInvoice = targetInvoiceUi,
            transactions = transactionsUi,
        )
    }

    override suspend fun toUi(
        operations: List<Operation>,
        perspective: OperationPerspective,
    ): List<OperationUi> {
        if (operations.isEmpty()) return emptyList()
        // Batch fetch IDs
        val categoryIds = operations.flatMap {
            listOfNotNull(it.categoryId) + it.transactions.mapNotNull { tx -> tx.categoryId }
        }.toSet()
        val accountIds = operations.flatMap {
            listOfNotNull(it.sourceAccountId) + it.transactions.mapNotNull { tx -> tx.accountId }
        }.toSet()
        val creditCardIds = operations.flatMap {
            listOfNotNull(it.targetCreditCardId) + it.transactions.mapNotNull { tx -> tx.creditCardId }
        }.toSet()
        val invoiceIds = operations.flatMap {
            listOfNotNull(it.targetInvoiceId) + it.transactions.mapNotNull { tx -> tx.invoiceId }
        }.toSet()

        val categories = categoryRepository.getAllCategories().filter { it.id in categoryIds }.associateBy { it.id }
        val accounts = accountRepository.getAllAccounts().filter { it.id in accountIds }.associateBy { it.id }
        val creditCards = creditCardRepository.getAllCreditCards().filter { it.id in creditCardIds }.associateBy { it.id }
        val invoices = invoiceRepository.getAllInvoices().filter { it.id in invoiceIds }.associateBy { it.id }
        val invoiceUis = invoices.mapValues { (_, invoice) -> invoiceUiMapper.toUi(invoice) }

        return operations.map { op ->
            val transactionsUi = op.transactions.map { tx ->
                TransactionUi(
                    transaction = tx,
                    account = tx.accountId?.let { accounts[it] },
                    category = tx.categoryId?.let { categories[it] },
                    creditCard = tx.creditCardId?.let { creditCards[it] },
                    invoice = tx.invoiceId?.let { invoiceUis[it] },
                )
            }
            OperationUi(
                operation = op,
                perspective = perspective,
                category = op.categoryId?.let { categories[it] },
                sourceAccount = op.sourceAccountId?.let { accounts[it] },
                targetCreditCard = op.targetCreditCardId?.let { creditCards[it] },
                targetInvoice = op.targetInvoiceId?.let { invoiceUis[it] },
                transactions = transactionsUi,
            )
        }
    }
}
