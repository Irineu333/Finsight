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
    suspend fun rewriteEntries(transactionId: Long, legs: List<TransactionLeg>, contra: ContraLeg?) {
        entryDao.deleteByTransactionId(transactionId)
        writeEntries(transactionId, legs, contra)
    }

    suspend fun writeEntries(
        transactionId: Long,
        legs: List<TransactionLeg>,
        contra: ContraLeg?,
    ) {
        // A transaction is a *balanced set*, and the empty set balances vacuously —
        // which is how an intent with no legs used to reach the database as a
        // transaction with no entries at all (spec `balanced-ledger`: never fewer
        // than two). Refused here, where every write passes.
        if (legs.isEmpty()) throw UnbalancedTransactionException(LedgerError.Unbalanced)

        val entries = buildList {
            legs.forEach { leg ->
                // The currency comes off the account the leg posts to, read from the
                // row `orRejectIfClosed` already loads to check closure — no extra
                // read, and no field on `TransactionLeg` to say it with (design D5).
                // "Post 100 USD to a BRL account" stops being refused and becomes
                // impossible to *utter*.
                val account = leg.accountId.orRejectIfClosed()
                add(
                    EntryEntity(
                        transactionId = transactionId,
                        accountId = account.id,
                        amount = leg.ledgerAmount(),
                        currency = account.currency,
                        dimensionId = leg.dimensionId,
                    )
                )
            }
            // Single-leg transactions need a synthesized contra leg to balance. It is
            // denominated in the same currency as the leg it balances — which is what
            // keeps a one-sided intent monomorphic by construction: an expense, an
            // income and an adjustment can never cross currencies.
            if (legs.size == 1) {
                val leg = legs.first()
                val counterpart = contra
                    ?: throw UnbalancedTransactionException(LedgerError.Unbalanced)
                val currency = first().currency
                add(
                    EntryEntity(
                        transactionId = transactionId,
                        accountId = systemAccountId(counterpart.nature, currency),
                        amount = -leg.ledgerAmount(),
                        currency = currency,
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
    private suspend fun Long.orRejectIfClosed(): AccountEntity {
        val account = accountDao.getAccountById(this)
            ?: throw UnbalancedTransactionException(LedgerError.Unbalanced)
        if (account.isArchived) {
            throw ClosedAccountException(LedgerError.ClosedAccount(ClosedFacade.of(account.type.toDomain())))
        }
        return account
    }

    /**
     * The single account of a given nature the app keeps for itself: the two
     * nominals and reconciliation, created on demand. Their names are lookup keys,
     * never rendered (design D10).
     */
    private suspend fun systemAccountId(nature: AccountType, currency: String): Long = when (nature) {
        AccountType.EXPENSE ->
            ensureSystemAccount(SystemAccount.EXPENSES, AccountEntity.Type.EXPENSE, currency)

        AccountType.INCOME ->
            ensureSystemAccount(SystemAccount.INCOMES, AccountEntity.Type.INCOME, currency)

        AccountType.EQUITY ->
            ensureSystemAccount(SystemAccount.RECONCILIATION, AccountEntity.Type.EQUITY, currency)
        // ASSET and LIABILITY are the user's own rows: there is no system account of
        // that nature to complete an intent with. CONVERSION is a system row, but it
        // is never a contra-leg nature — it is reached only by the cross-currency
        // completion, which resolves it by currency rather than by nature.
        AccountType.ASSET, AccountType.LIABILITY, AccountType.CONVERSION ->
            throw UnbalancedTransactionException(LedgerError.Unbalanced)
    }

    private suspend fun ensureSystemAccount(
        name: String,
        type: AccountEntity.Type,
        currency: String,
    ): Long {
        accountDao.getByTypeAndName(type, name)?.let { return it.id }
        return accountDao.insert(AccountEntity(name = name, type = type, currency = currency))
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
