@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.database.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.room.RoomDatabase
import com.neoutils.finsight.database.dao.TransactionDao
import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.mapper.TransactionMapper
import com.neoutils.finsight.extension.closedLegBlockingChange
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.ClosedFacade
import com.neoutils.finsight.domain.error.LedgerError
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.ledger.DimensionWriteGuard
import com.neoutils.finsight.domain.ledger.LedgerWrite
import com.neoutils.finsight.domain.ledger.TransactionRemovalHook
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine as flowCombine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class TransactionRepository(
    // The supertype, not the app's concrete `@Database`: opening a write transaction
    // is a Room capability, and the ledger has no business knowing which schema it
    // is part of (confirmed by the spike, design D13e).
    private val database: RoomDatabase,
    private val transactionDao: TransactionDao,
    private val entryDao: EntryDao,
    private val accountRepository: IAccountRepository,
    private val writeGuard: DimensionWriteGuard,
    private val removalHook: TransactionRemovalHook,
    private val transactionMapper: TransactionMapper,
    private val ledgerEntryWriter: LedgerEntryWriter,
) : ITransactionRepository {

    // The whole chart of accounts, not the ASSET facade: an entry whose account is
    // missing from this map is dropped, and a card purchase has no asset leg.
    private val accountsFlow = accountRepository.observeAllLedgerAccounts().map { it.associateBy { account -> account.id } }

    private fun List<EntryEntity>.toDomainEntries(accounts: Map<Long, Account>): List<Entry> =
        mapNotNull { entity ->
            accounts[entity.accountId]?.let { account ->
                Entry(
                    id = entity.id,
                    transactionId = entity.transactionId,
                    account = account,
                    amount = entity.amount,
                    currency = entity.currency,
                    dimensionId = entity.dimensionId,
                )
            }
        }

    /**
     * Row plus legs, and nothing else. The facade lookups this used to combine —
     * categories, cards, invoices, installments, recurring — are gone: each feature
     * resolves what it needs from the legs (design D6), and this flow no longer
     * re-emits every transaction in the app because a card was renamed.
     */
    private fun Flow<List<TransactionEntity>>.mapToDomain(): Flow<List<Transaction>> = flowCombine(
        this,
        accountsFlow,
        entryDao.observeAll(),
    ) { transactions, accounts, entries ->
        val entriesByTransactionId = entries.groupBy { it.transactionId }
        transactions.mapNotNull { transaction ->
            transactionMapper.toDomain(
                entity = transaction,
                entries = entriesByTransactionId[transaction.id].orEmpty().toDomainEntries(accounts),
            )
        }
    }

    override fun observeAllTransactions(): Flow<List<Transaction>> =
        transactionDao.observeAll().mapToDomain()

    override fun observeTransactionsBy(
        date: LocalDate?,
        dimensionId: Long?,
        accountId: Long?,
    ): Flow<List<Transaction>> = transactionDao.observeBy(
        date = date,
        dimensionId = dimensionId,
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
        recurringId = recurringId,
        recurringCycle = recurringCycle,
        installmentId = installmentId,
        installmentNumber = installmentNumber,
    )

    private suspend fun TransactionEntity.toDomain(accounts: Map<Long, Account>): Transaction? =
        transactionMapper.toDomain(
            entity = this,
            entries = entryDao.getByTransactionId(id).toDomainEntries(accounts),
        )

    override suspend fun getAllTransactions(): List<Transaction> {
        val accounts = accountRepository.getAllLedgerAccounts().associateBy { it.id }
        return transactionDao.getAll().mapNotNull { it.toDomain(accounts) }
    }

    override suspend fun getTransactionById(id: Long): Transaction? {
        val entity = transactionDao.getById(id) ?: return null
        return entity.toDomain(accountRepository.getAllLedgerAccounts().associateBy { it.id })
    }

    /**
     * The facade veto, asked at the single write boundary next to `Σ = 0` (design
     * D11/D23) so no screen or use case has to remember it. What the rule *is*
     * belongs to whoever owns the dimensions — this only guarantees there is one
     * place it gets asked.
     */
    private suspend fun ensureDimensionsAccept(dimensionIds: Set<Long>, settlesALiability: Boolean) {
        if (dimensionIds.isEmpty()) return
        writeGuard.ensureAccepts(LedgerWrite(dimensionIds, settlesALiability))
    }

    private suspend fun ensureDimensionsAccept(legs: List<TransactionLeg>) = ensureDimensionsAccept(
        dimensionIds = legs.mapNotNull { it.dimensionId }.toSet(),
        settlesALiability = legs.settlesALiability(),
    )

    /**
     * Removing a transaction changes its sub-ledgers too, so it passes the same
     * gate. It never *settles* one: undoing a payment is not the payment.
     */
    private suspend fun ensureDimensionsAcceptRemoval(id: Long) = ensureDimensionsAccept(
        dimensionIds = entryDao.getByTransactionId(id).mapNotNull { it.dimensionId }.toSet(),
        settlesALiability = false,
    )

    /**
     * The closure invariant, in the removal direction.
     *
     * `LedgerEntryWriter` refuses *new* entries on a closed account; nothing refused
     * taking them away, so deleting a transaction of an archived account reopened a
     * balance on it — the exact state `ArchiveAccountUseCase` refuses to create,
     * reached from the other side. The account then accepts no entries and shows in
     * no selector, so the user cannot zero it again.
     *
     * Only *permanent* accounts (ASSET/LIABILITY) are guarded, mirroring the
     * precondition to close them. A category closes at any balance, so removing its
     * movement strands nothing.
     */
    private suspend fun ensureClosedAccountsKeepTheirBalance(id: Long) {
        val accounts = accountRepository.getAllLedgerAccounts().associateBy { it.id }
        val blocking = entryDao.getByTransactionId(id)
            .toDomainEntries(accounts)
            .closedLegBlockingChange() ?: return

        throw ClosedAccountException(
            LedgerError.ClosedAccountRemoval(ClosedFacade.of(blocking.account.type))
        )
    }

    /**
     * Whether this intent settles a liability from an asset — the ledger shape of
     * paying a card.
     *
     * It used to read the user's own account-vs-card choice off the leg. With the
     * legs down to identities there is no such choice to read, and there never
     * needed to be one: the question is about the *nature* of the accounts the legs
     * post to, which the chart answers.
     *
     * This deliberately does **not** return a [TransactionLabel], and is not a
     * second implementation of the label rule: the entries do not exist yet, so
     * `deriveTransactionLabel` — which reads them — has nothing to read.
     */
    private suspend fun List<TransactionLeg>.settlesALiability(): Boolean {
        if (size < 2) return false
        val accounts = accountRepository.getAllLedgerAccounts().associateBy { it.id }
        val types = mapNotNull { accounts[it.accountId]?.type }
        return AccountType.ASSET in types && AccountType.LIABILITY in types
    }

    override suspend fun createTransaction(intent: TransactionIntent): Transaction {
        // Reject an unbalanced intent before writing anything (Σ = 0 per currency).
        ledgerEntryWriter.validate(intent.legs)
        ensureDimensionsAccept(intent.legs)

        // The transaction row and its ledger legs are written in a single transaction,
        // so a mid-way failure (missing facade row, cancellation, DB error) rolls back
        // everything, never leaving a transaction without its entries.
        val transactionId = database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val transactionId = transactionDao.insert(intent.toEntity())

                ledgerEntryWriter.writeEntries(transactionId, intent.legs, intent.contra)

                transactionId
            }
        }

        return getTransactionById(transactionId)!!
    }

    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> {
        intents.forEach {
            ledgerEntryWriter.validate(it.legs)
            ensureDimensionsAccept(it.legs)
        }

        val ids = database.useWriterConnection { connection ->
            connection.immediateTransaction {
                intents.map { intent ->
                    val transactionId = transactionDao.insert(intent.toEntity())
                    ledgerEntryWriter.writeEntries(transactionId, intent.legs, intent.contra)
                    transactionId
                }
            }
        }

        val accounts = accountRepository.getAllLedgerAccounts().associateBy { it.id }
        return ids.mapNotNull { transactionDao.getById(it)?.toDomain(accounts) }
    }

    override suspend fun updateTransaction(
        id: Long,
        title: String?,
        date: LocalDate,
        leg: TransactionLeg,
        contra: ContraLeg?,
    ) {
        // An edit has two sides, and both are changes to an invoice: the one losing
        // the old entries and the one gaining the new. Checking only the new side let
        // a purchase be moved *off* a paid invoice, silently removing money from
        // settled history — the rewrite deletes the old entries either way.
        ensureDimensionsAcceptRemoval(id)
        // Same two-sided reasoning, for closure: the rewrite takes the old legs away,
        // so pointing an old transaction at a different account is how its archived
        // account got its balance back — the new legs are all open, so nothing else
        // here objected.
        ensureClosedAccountsKeepTheirBalance(id)
        // Editing is never the payment that settles a closed invoice; that exception
        // exists only for creating it (task 5.6: CLOSED/PAID blocks editing too).
        ensureDimensionsAccept(
            dimensionIds = setOfNotNull(leg.dimensionId),
            settlesALiability = false,
        )

        // Update and ledger rewrite (delete + re-insert legs) share one transaction, so a
        // failure never leaves the transaction with its old legs deleted and no new ones.
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                transactionDao.update(
                    id = id,
                    title = title,
                    date = date,
                )
                ledgerEntryWriter.rewriteEntries(id, listOf(leg), contra)
            }
        }
    }

    override suspend fun deleteTransactionById(id: Long) {
        // The removal and whatever a facade has to correct because of it are one
        // transaction: a failure between them would leave that facade describing
        // rows that no longer exist.
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
        ensureDimensionsAcceptRemoval(id)
        ensureClosedAccountsKeepTheirBalance(id)

        // Read whole before deleting: the entries go with the row (CASCADE), and a
        // facade correcting itself may need what they said.
        val accounts = accountRepository.getAllLedgerAccounts().associateBy { it.id }
        val removed = transactionDao.getById(id)?.toDomain(accounts)

        transactionDao.deleteById(id)

        removed?.let { removalHook.onRemoved(it) }
    }

    override suspend fun deleteTransactionsByIds(ids: List<Long>) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                ids.forEach { removeRow(it) }
            }
        }
    }

}
