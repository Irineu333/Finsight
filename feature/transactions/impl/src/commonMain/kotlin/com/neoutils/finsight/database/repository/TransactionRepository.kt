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
import com.neoutils.finsight.database.mapper.TransactionMapper
import com.neoutils.finsight.database.mapper.RecurringMapper
import com.neoutils.finsight.domain.error.InvoiceLockedException
import com.neoutils.finsight.domain.error.LedgerError
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine as flowCombine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class TransactionRepository(
    private val database: AppDatabase,
    private val transactionDao: TransactionDao,
    private val entryDao: EntryDao,
    private val recurringDao: RecurringDao,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val installmentRepository: IInstallmentRepository,
    private val accountRepository: IAccountRepository,
    private val transactionMapper: TransactionMapper,
    private val recurringMapper: RecurringMapper,
    private val ledgerEntryWriter: LedgerEntryWriter,
) : ITransactionRepository {

    // Resolving a historical reference, not offering a choice: closed included.
    private val categoriesFlow = categoryRepository.observeAllCategoriesIncludingClosed().map { it.associateBy { category -> category.id } }
    private val creditCardsFlow = creditCardRepository.observeAllCreditCardsIncludingClosed().map { it.associateBy { card -> card.id } }
    private val invoicesFlow = invoiceRepository.observeAllInvoices().map { it.associateBy { invoice -> invoice.id } }
    private val installmentsFlow = installmentRepository.observeAllInstallments().map { it.associateBy { installment -> installment.id } }
    // The whole chart of accounts, not the ASSET facade: an entry whose account is
    // missing from this map is dropped, and a card purchase has no asset leg.
    private val accountsFlow = accountRepository.observeAllLedgerAccounts().map { it.associateBy { account -> account.id } }

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

    private fun Flow<List<TransactionEntity>>.mapToDomain(): Flow<List<Transaction>> = combine(
        this,
        categoriesFlow,
        creditCardsFlow,
        invoicesFlow,
        installmentsFlow,
        accountsFlow,
        recurringFlow,
        entryDao.observeAll(),
    ) { transactions, categories, creditCards, invoices, installments, accounts, recurring, entries ->
        val entriesByTransactionId = entries.groupBy { it.transactionId }
        transactions.mapNotNull { transaction ->
            transactionMapper.toDomain(
                entity = transaction,
                categories = categories,
                creditCards = creditCards,
                invoices = invoices,
                installments = installments,
                recurring = recurring,
                entries = entriesByTransactionId[transaction.id].orEmpty().toDomainEntries(accounts),
            )
        }
    }

    override fun observeAllTransactions(): Flow<List<Transaction>> =
        transactionDao.observeAll().mapToDomain()

    override fun observeTransactionsBy(
        date: LocalDate?,
        invoiceId: Long?,
        creditCardId: Long?,
        accountId: Long?,
    ): Flow<List<Transaction>> = transactionDao.observeBy(
        date = date,
        invoiceId = invoiceId,
        creditCardId = creditCardId,
        accountId = accountId,
    ).mapToDomain()

    override fun observeTransactionById(id: Long): Flow<Transaction?> {
        return observeAllTransactions()
            .map { transactions -> transactions.firstOrNull { it.id == id } }
            // Derived from the full list, so it re-runs on any transaction/lookup change; only notify
            // consumers when the target actually changed.
            .distinctUntilChanged()
    }

    private fun TransactionIntent.toEntity() = TransactionEntity(
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
        val categories = categoryRepository.getAllCategoriesIncludingClosed().associateBy { it.id }
        val creditCards = creditCardRepository.getAllCreditCardsIncludingClosed().associateBy { it.id }
        val accounts = accountRepository.getAllLedgerAccounts().associateBy { it.id }
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

    private suspend fun TransactionEntity.toDomain(lookups: Lookups): Transaction? = transactionMapper.toDomain(
        entity = this,
        categories = lookups.categories,
        creditCards = lookups.creditCards,
        invoices = lookups.invoices,
        installments = lookups.installments,
        recurring = lookups.recurring,
        entries = entryDao.getByTransactionId(id).toDomainEntries(lookups.accounts),
    )

    override suspend fun getAllTransactions(): List<Transaction> {
        val lookups = lookups()
        return transactionDao.getAll().mapNotNull { it.toDomain(lookups) }
    }

    override suspend fun getTransactionById(id: Long): Transaction? {
        val entity = transactionDao.getById(id) ?: return null
        return entity.toDomain(lookups())
    }

    /**
     * The invoice-status invariant, enforced at the single write boundary next to
     * `Σ = 0` (design D23) so no screen or use case has to remember it:
     *
     *  - `PAID` — settled history: nothing may touch it.
     *  - `CLOSED` — takes no new spending, but must still accept the payment that
     *    settles it, which is the whole point of closing.
     *  - anything else — free.
     *
     * `CLOSED` and `PAID` behave differently here, which is why `isClosedToNewExpenses` (which
     * fuses them) is not the predicate: it happens to be right only for creating
     * an expense, where the two coincide.
     */
    private suspend fun ensureInvoicesAccept(invoiceIds: Set<Long>, isPayment: Boolean) {
        invoiceIds.forEach { invoiceId ->
            val status = invoiceRepository.getInvoiceById(invoiceId)?.status ?: return@forEach
            when {
                status.isPaid -> throw InvoiceLockedException(LedgerError.PaidInvoice)
                status.isClosed && !isPayment -> throw InvoiceLockedException(LedgerError.ClosedInvoice)
            }
        }
    }

    private suspend fun ensureInvoiceAccepts(legs: List<TransactionLeg>) = ensureInvoicesAccept(
        invoiceIds = legs.mapNotNull { it.invoice?.id }.toSet(),
        isPayment = legs.settlesACard(),
    )

    /**
     * Removing a transaction changes its invoice too, so it passes the same gate.
     * `isPayment` is false because *un*-paying a closed invoice is not the payment
     * that settles it — a closed invoice is immutable in both directions.
     */
    private suspend fun ensureInvoiceAcceptsRemoval(id: Long) = ensureInvoicesAccept(
        invoiceIds = entryDao.getByTransactionId(id).mapNotNull { it.invoiceId }.toSet(),
        isPayment = false,
    )

    /**
     * Whether this intent pays a card from an account — an account leg and a card
     * leg on the same transaction.
     *
     * This deliberately does **not** return a [TransactionLabel], and is not a
     * second implementation of the label rule: at this point the entries do not
     * exist yet (the accounts are resolved by the writer, below), so
     * `deriveTransactionLabel` — which reads `AccountType` — has nothing to read.
     * It answers one boolean question the gate needs, from the only thing
     * available here: the user's own account-vs-card choice.
     */
    private fun List<TransactionLeg>.settlesACard(): Boolean =
        size >= 2 && any { it.target.isAccount } && any { it.target.isCreditCard }

    override suspend fun createTransaction(intent: TransactionIntent): Transaction {
        // Reject an unbalanced intent before writing anything (Σ = 0 per currency).
        ledgerEntryWriter.validate(intent.legs)
        ensureInvoiceAccepts(intent.legs)

        // The transaction row and its ledger legs are written in a single transaction,
        // so a mid-way failure (missing facade row, cancellation, DB error) rolls back
        // everything, never leaving a transaction without its entries.
        val transactionId = database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val transactionId = transactionDao.insert(intent.toEntity())

                ledgerEntryWriter.writeEntries(transactionId, intent.legs)

                transactionId
            }
        }

        return getTransactionById(transactionId)!!
    }

    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> {
        intents.forEach {
            ledgerEntryWriter.validate(it.legs)
            ensureInvoiceAccepts(it.legs)
        }

        val ids = database.useWriterConnection { connection ->
            connection.immediateTransaction {
                intents.map { intent ->
                    val transactionId = transactionDao.insert(intent.toEntity())
                    ledgerEntryWriter.writeEntries(transactionId, intent.legs)
                    transactionId
                }
            }
        }

        val lookups = lookups()
        return ids.mapNotNull { transactionDao.getById(it)?.toDomain(lookups) }
    }

    override suspend fun updateTransaction(id: Long, title: String?, date: LocalDate, leg: TransactionLeg) {
        // An edit has two sides, and both are changes to an invoice: the one losing
        // the old entries and the one gaining the new. Checking only the new side let
        // a purchase be moved *off* a paid invoice, silently removing money from
        // settled history — the rewrite deletes the old entries either way.
        ensureInvoiceAcceptsRemoval(id)
        // Editing is never the payment that settles a closed invoice; that exception
        // exists only for creating it (task 5.6: CLOSED/PAID blocks editing too).
        ensureInvoicesAccept(
            invoiceIds = listOfNotNull(leg.invoice?.id).toSet(),
            isPayment = false,
        )

        // Update and ledger rewrite (delete + re-insert legs) share one transaction, so a
        // failure never leaves the transaction with its old legs deleted and no new ones.
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

    override suspend fun deleteTransactionById(id: Long) {
        // The installment bookkeeping and the row removal are one transaction: a
        // failure between them would leave the installment's count and total
        // describing transactions that no longer exist.
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                removeRow(id)
            }
        }
    }

    /**
     * The row removal itself, assuming the caller already holds the writer
     * transaction — so a bulk delete stays one unit instead of N.
     */
    private suspend fun removeRow(id: Long) {
        ensureInvoiceAcceptsRemoval(id)

        val transaction = transactionDao.getById(id)
        val installmentId = transaction?.installmentId

        if (installmentId == null) {
            transactionDao.deleteById(id)
            return
        }

        // The transaction's own share of the installment, from the ledger.
        val transactionAmount = entryDao.getByTransactionId(id)
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
                    totalAmount = installment.totalAmount - transactionAmount,
                )
            }
        }
    }

    override suspend fun deleteTransactionsByIds(ids: List<Long>) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                ids.forEach { removeRow(it) }
            }
        }
    }

}
