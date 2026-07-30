package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.database.dao.DimensionDao
import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.mapper.toDomain
import com.neoutils.finsight.database.mapper.toEntity
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
                // The currency of a leg is read off the account it posts on — from the
                // row `orRejectIfClosed` already loads, so no extra read. The intent
                // cannot state a currency, which is what makes "post 100 USD on a BRL
                // account" unexpressible rather than merely refused (design D5).
                val account = leg.accountId.accountOrRejectIfClosed()
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
            // Single-leg transactions need a synthesized contra leg to balance. It posts
            // on the nominal of the *leg's own* currency, which is why an expense paid
            // from a BRL account is a single-currency operation whatever it bought.
            if (legs.size == 1) {
                val leg = legs.first()
                val counterpart = contra
                    ?: throw UnbalancedTransactionException(LedgerError.Unbalanced)
                val currency = first().currency
                add(
                    EntryEntity(
                        transactionId = transactionId,
                        accountId = systemAccountId(counterpart.systemAccount(), currency),
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

        val completed = entries + conversionLegsFor(transactionId, entries)

        // The invariant is Σ = 0 PER CURRENCY (not a flat scalar), and it admits no
        // exception — a cross-currency operation arrives *incomplete* and is completed
        // above, never tolerated unbalanced here.
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
     * The legs that complete an operation crossing currencies: for each currency still
     * owing something, the opposite of that residue, on the conversion account of **that**
     * currency (design D1, D3).
     *
     * Three properties make this the whole rule, with no branch per use case:
     *
     * - it is computed **last, by difference**, never independently and then compared.
     *   That is what concentrates every rounding error of the exchange in one place by
     *   construction, instead of letting it surface as a few cents of imbalance;
     * - it carries **no dimension** (design D15). Inheriting the dimension of the leg
     *   whose residue it absorbs would make every cross-currency invoice payment fail the
     *   landing rule — `INVOICE` only lands on `LIABILITY` — and would count the cost of
     *   changing money as debt on the card;
     * - with a **single** currency present it returns nothing at all, so single-currency
     *   writes stay byte for byte what they were. A non-zero residue there is an
     *   imbalance, and the check below refuses it rather than papering over it with a
     *   synthesized leg.
     *
     * A currency already settled gets no leg: there is no residue to absorb, and a
     * zero-amount entry would create a chart row no figure ever reads.
     */
    private suspend fun conversionLegsFor(
        transactionId: Long,
        entries: List<EntryEntity>,
    ): List<EntryEntity> {
        val residues = entries
            .groupBy { it.currency }
            .mapValues { (_, group) -> group.sumOf { it.amount } }

        if (residues.size < 2) return emptyList()

        val owing = residues.filterValues { it != 0L }
        if (owing.isEmpty()) return emptyList()

        // Evaluated before any account is materialized: the boundary writes nothing on
        // failure, and a refused intent must not leave conversion rows behind for an
        // operation it did not complete.
        if (owing.values.all { it > 0L } || owing.values.all { it < 0L }) {
            throw UnbalancedTransactionException(LedgerError.ImpossibleExchange)
        }

        return owing.map { (currency, residue) ->
            EntryEntity(
                transactionId = transactionId,
                accountId = systemAccountId(SystemAccount.CONVERSION, currency),
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
    private suspend fun Long.accountOrRejectIfClosed(): AccountEntity {
        val account = accountDao.getAccountById(this)
            ?: throw UnbalancedTransactionException(LedgerError.Unbalanced)
        if (account.isArchived) {
            throw ClosedAccountException(LedgerError.ClosedAccount(ClosedFacade.of(account.type.toDomain())))
        }
        return account
    }

    /**
     * Which system account a declared contra leg asks for.
     *
     * The nature alone no longer answers it — `EQUITY` has two system accounts per
     * currency — so the mapping is stated here rather than derived. `CONVERSION` is
     * refused on purpose: a conversion account exists only because *this* boundary
     * completed a cross-currency operation, and nothing outside may name it.
     */
    private fun ContraLeg.systemAccount(): SystemAccount = when (nature) {
        AccountType.EXPENSE -> SystemAccount.EXPENSES
        AccountType.INCOME -> SystemAccount.INCOMES
        AccountType.EQUITY -> SystemAccount.RECONCILIATION
        // ASSET and LIABILITY are the user's own rows, and a conversion row is the
        // boundary's alone: none of the three is a contra an intent may declare.
        AccountType.ASSET, AccountType.LIABILITY, AccountType.CONVERSION ->
            throw UnbalancedTransactionException(LedgerError.Unbalanced)
    }

    /**
     * The account the app keeps for itself under [account], **in [currency]** — created
     * on demand, one row per currency (design D3/D4). Their names are lookup keys, never
     * rendered (design D10).
     *
     * One per currency is what makes `Account.currency` mean the same thing in every line
     * of the chart: with a single nominal, an expense in USD would land on a row whose
     * `currency` said BRL, and the column would mean a real constraint on the user's
     * accounts and a meaningless label on the system ones.
     */
    private suspend fun systemAccountId(account: SystemAccount, currency: String): Long {
        val type = account.nature.toEntity()
        accountDao.getByTypeNameAndCurrency(type, account.accountName, currency)
            ?.let { return it.id }
        return accountDao.insert(
            AccountEntity(name = account.accountName, type = type, currency = currency)
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
