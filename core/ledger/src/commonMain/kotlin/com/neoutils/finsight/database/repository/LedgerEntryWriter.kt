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
                        accountId = contraAccountId(counterpart.nature, currency),
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

        val completed = entries.completedPerCurrency(transactionId)

        // The invariant is Σ = 0 PER CURRENCY, and it admits no exception — the
        // crossing included. What `completedPerCurrency` does is *complete* an
        // incomplete intent, never relax this.
        val balancedPerCurrency = completed
            .groupBy { it.currency }
            .all { (_, group) -> group.sumOf { it.amount } == 0L }
        if (!balancedPerCurrency) {
            throw UnbalancedTransactionException(LedgerError.Unbalanced)
        }

        completed.forEach { rejectIfDimensionLandsWrong(it) }

        entryDao.insertAll(completed)
    }

    /**
     * The entries, plus whatever it takes to make `Σ = 0` true for every currency.
     *
     * A transaction that crosses currencies does not unbalance the ledger; it arrives
     * here **incomplete**, exactly as a one-sided intent does, and completing an
     * incomplete intent is what this boundary is for. The rule is uniform, with no
     * branch per use case:
     *
     * 1. group the legs by currency and take each group's residue;
     * 2. **one currency** → the residue must be zero. This is today's behaviour,
     *    untouched: an expense, an income and an adjustment are monomorphic by
     *    construction, so nothing is ever synthesized to paper over a plain
     *    unbalanced intent;
     * 3. **two or more** → post the negation of each currency's residue to that
     *    currency's conversion account, created on demand.
     *
     * The conversion leg is always the **last leg computed** and takes the residue
     * **by difference** — never computed independently and then compared. That is
     * what concentrates every rounding error in the system in one place by
     * construction, instead of letting it surface as an off-by-cents imbalance. It is
     * where GnuCash, Beancount and hledger all have catalogued defects.
     *
     * It carries **no dimension** (design D15). Without that rule a cross-currency
     * invoice payment would not persist at all: the `LIABILITY` leg carries the
     * invoice's dimension, and copying it onto the residue leg would make
     * `rejectIfDimensionLandsWrong` refuse the whole transaction, since
     * `DimensionKind.INVOICE.landsOn` is `{LIABILITY}`. It is also what makes
     * accounting sense — the exchange residue belongs to the exchange, not to the
     * invoice.
     *
     * Step 3 would balance *anything*, a typo included, which is why it carries the
     * one new guard: the residues must not all share a sign. If every currency
     * involved gains value, the intent creates money without a source — not an
     * exchange, a defect — and nothing is written.
     */
    private suspend fun List<EntryEntity>.completedPerCurrency(
        transactionId: Long,
    ): List<EntryEntity> {
        val residues = groupBy { it.currency }
            .mapValues { (_, group) -> group.sumOf { it.amount } }
            .filterValues { it != 0L }

        if (residues.isEmpty()) return this

        // A single currency present and a residue left over is a plain unbalanced
        // intent, and stays refused with the same typed error it has always had.
        if (distinctBy { it.currency }.size < 2) {
            throw UnbalancedTransactionException(LedgerError.Unbalanced)
        }

        if (residues.values.all { it > 0L } || residues.values.all { it < 0L }) {
            throw UnbalancedTransactionException(LedgerError.SameSignResidues)
        }

        return this + residues.map { (currency, residue) ->
            EntryEntity(
                transactionId = transactionId,
                accountId = conversionAccountId(currency),
                amount = -residue,
                currency = currency,
                dimensionId = null,
            )
        }
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
     * Which system account a **contra leg** names, from the nature it asked for.
     *
     * The nature is no longer what *resolves* an account — resolution is by
     * `(SystemAccount, currency)`, below — it is only how a one-sided intent says
     * which of the app's own rows should complete it. Conversion is unreachable from
     * here by construction: it is not a contra-leg nature, and it is not resolved by
     * nature either, which is precisely what a type of its own buys (design D4).
     */
    private suspend fun contraAccountId(nature: AccountType, currency: String): Long = when (nature) {
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

    /**
     * Where the residue of a currency crossing lands: one account per currency, under
     * the account type that exists precisely so none of the four behaviours reading
     * `EQUITY` as "adjustment" has to change (design D2).
     */
    private suspend fun conversionAccountId(currency: String): Long =
        ensureSystemAccount(SystemAccount.CONVERSION, AccountEntity.Type.CONVERSION, currency)

    /**
     * The app's own row for `(SystemAccount, currency)`, created on demand.
     *
     * The currency is part of the identity, so there are two nominals **per currency
     * in use** rather than two in the whole app. `CLOSED_ACCOUNT`/`CLOSED_CARD`,
     * artefacts of the `v7 → v10` upgrade, are not touched: they exist in BRL and
     * simply become the BRL ones, with no migration.
     *
     * Like GnuCash's trading accounts, these are created by the bookkeeper and are
     * not postable by hand: the names are lookup keys, never rendered, and no
     * selector offers them (design D10).
     */
    private suspend fun ensureSystemAccount(
        name: String,
        type: AccountEntity.Type,
        currency: String,
    ): Long {
        accountDao.getByTypeAndName(type, name, currency)?.let { return it.id }
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
