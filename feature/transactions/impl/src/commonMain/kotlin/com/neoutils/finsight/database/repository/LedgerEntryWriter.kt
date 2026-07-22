package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.database.dao.DimensionDao
import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.mapper.toDomain
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.ClosedFacade
import com.neoutils.finsight.domain.error.LedgerError
import com.neoutils.finsight.domain.error.UnbalancedTransactionException
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.BASE_CURRENCY
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.DimensionKind
import com.neoutils.finsight.domain.model.SystemAccount
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.TransactionLeg
import kotlin.math.roundToLong

/**
 * The single write-boundary that turns the user's intent into balanced
 * double-entry [EntryEntity] rows.
 *
 * Every leg arrives as an identity — an account id and, at most, a dimension id
 * (design D6) — so there is nothing here to look a facade up with, and nothing that
 * could name one. What remains is what genuinely belongs at a write boundary:
 * completing a one-sided intent (which creates the system account on demand),
 * applying the one sign rule, and enforcing the two invariants — `Σ = 0` per
 * currency and the dimension landing rule — throwing and writing nothing on failure.
 */
class LedgerEntryWriter(
    private val entryDao: EntryDao,
    private val accountDao: AccountDao,
    private val dimensionDao: DimensionDao,
) {

    /**
     * Validates the balance invariant on the raw legs before any row is written,
     * so an unbalanced multi-leg transaction is rejected without side effects.
     */
    fun validate(legs: List<TransactionLeg>) {
        if (legs.size < 2) return
        val total = legs.sumOf { it.ledgerAmount() }
        if (total != 0L) throw UnbalancedTransactionException(LedgerError.Unbalanced)
    }

    /** Rebuilds the entries of [transactionId] from its (edited) legs. */
    suspend fun rewriteEntries(transactionId: Long, legs: List<TransactionLeg>, contra: ContraLeg? = null) {
        entryDao.deleteByTransactionId(transactionId)
        writeEntries(transactionId, legs, contra)
    }

    suspend fun writeEntries(
        transactionId: Long,
        legs: List<TransactionLeg>,
        contra: ContraLeg? = null,
    ) {
        val entries = buildList {
            legs.forEach { leg ->
                add(
                    EntryEntity(
                        transactionId = transactionId,
                        accountId = leg.accountId.orRejectIfClosed(),
                        amount = leg.ledgerAmount(),
                        currency = BASE_CURRENCY,
                        dimensionId = leg.dimensionId,
                    )
                )
            }
            // Single-leg transactions need a synthesized contra leg to balance.
            if (legs.size == 1) {
                val leg = legs.first()
                val counterpart = contra
                    ?: throw UnbalancedTransactionException(LedgerError.Unbalanced)
                add(
                    EntryEntity(
                        transactionId = transactionId,
                        accountId = systemAccountId(counterpart.nature),
                        amount = -leg.ledgerAmount(),
                        currency = BASE_CURRENCY,
                        // A nominal leg is classified by the category's dimension.
                        // No dimension means genuinely unclassified — there is no
                        // bucket account and no bucket dimension standing in for it.
                        dimensionId = counterpart.dimensionId,
                    )
                )
            }
        }

        // The invariant is Σ = 0 PER CURRENCY (not a flat scalar), so the boundary
        // stays correct once more than the base currency exists.
        val balancedPerCurrency = entries
            .groupBy { it.currency }
            .all { (_, group) -> group.sumOf { it.amount } == 0L }
        if (!balancedPerCurrency) {
            throw UnbalancedTransactionException(LedgerError.Unbalanced)
        }

        entries.forEach { rejectIfDimensionLandsWrong(it) }

        entryDao.insertAll(entries)
    }

    /**
     * A dimension may only land on an account of a nature its kind accepts
     * ([DimensionKind.landsOn]). Uniform, with no branch per kind: the ledger never
     * asks what an `INVOICE` *is*, only where one may sit.
     *
     * Without this the rule would be the writer's discipline rather than the
     * schema's — and its violation is silent. An invoice dimension landing on a
     * nominal leg produces no error at all; it just makes every sum by that
     * dimension quietly wrong. That is the defect class the kind exists to kill,
     * so the check belongs beside the zero-sum one, at the same single boundary.
     */
    private suspend fun rejectIfDimensionLandsWrong(entry: EntryEntity) {
        val dimensionId = entry.dimensionId ?: return
        val kind = dimensionDao.getById(dimensionId)?.kind
            ?: throw UnbalancedTransactionException(LedgerError.MisplacedDimension)
        val type = accountDao.getAccountById(entry.accountId)?.type?.toDomain()
            ?: throw UnbalancedTransactionException(LedgerError.MisplacedDimension)
        if (type !in kind.landsOn) {
            throw UnbalancedTransactionException(LedgerError.MisplacedDimension)
        }
    }

    /**
     * Closure is checked where every leg of every write passes — not in the screens
     * that happen to offer the action. A closed account keeps its history; it just
     * receives nothing new.
     *
     * Only the **monetary** accounts are checked. Closing an ASSET/LIABILITY
     * requires a zero balance, so a new entry there strands money; a nominal account
     * is never closed at all, so nothing there could break. Which facade the account
     * belongs to comes from its nature — the ledger reports what it knows, and the
     * error carries it so the screen can say the right word.
     */
    private suspend fun Long.orRejectIfClosed(): Long = also { accountId ->
        val account = accountDao.getAccountById(accountId)
            ?: throw UnbalancedTransactionException(LedgerError.Unbalanced)
        if (account.isArchived) {
            throw ClosedAccountException(LedgerError.ClosedAccount(ClosedFacade.of(account.type.toDomain())))
        }
    }

    /**
     * The single account of a given nature the app keeps for itself: the two
     * nominals and reconciliation, created on demand. Their names are lookup keys,
     * never rendered (design D10).
     */
    private suspend fun systemAccountId(nature: AccountType): Long = when (nature) {
        AccountType.EXPENSE -> ensureSystemAccount(SystemAccount.EXPENSES, AccountEntity.Type.EXPENSE)
        AccountType.INCOME -> ensureSystemAccount(SystemAccount.INCOMES, AccountEntity.Type.INCOME)
        AccountType.EQUITY -> ensureSystemAccount(SystemAccount.RECONCILIATION, AccountEntity.Type.EQUITY)
        // ASSET and LIABILITY are the user's own rows: there is no system account of
        // that nature to complete an intent with.
        AccountType.ASSET, AccountType.LIABILITY ->
            throw UnbalancedTransactionException(LedgerError.Unbalanced)
    }

    private suspend fun ensureSystemAccount(name: String, type: AccountEntity.Type): Long {
        accountDao.getByTypeAndName(type, name)?.let { return it.id }
        return accountDao.insert(
            AccountEntity(name = name, type = type, currency = BASE_CURRENCY)
        )
    }

    /**
     * The signed amount, in cents, that a leg of the user's intent contributes to
     * the natural (debit-positive) balance of its own account. This is the only
     * place the input vocabulary ([TransactionType]) becomes a ledger sign.
     */
    private fun TransactionLeg.ledgerAmount(): Long {
        val cents = (amount * 100).roundToLong()
        return when (type) {
            TransactionType.EXPENSE -> -cents
            TransactionType.INCOME -> cents
            TransactionType.ADJUSTMENT -> cents
        }
    }
}
