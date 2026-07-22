package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.database.dao.CreditCardDao
import com.neoutils.finsight.database.dao.DimensionDao
import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.mapper.toDomain
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.ClosedFacade
import com.neoutils.finsight.domain.error.LedgerError
import com.neoutils.finsight.domain.error.UnbalancedTransactionException
import com.neoutils.finsight.domain.model.BASE_CURRENCY
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.DimensionKind
import com.neoutils.finsight.domain.model.SystemAccount
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.TransactionLeg
import kotlin.math.roundToLong

/**
 * The single write-boundary that turns the user's intent into balanced
 * double-entry [EntryEntity] rows.
 *
 * It resolves each leg to its chart-of-accounts row, synthesizes the contra leg
 * for a single-leg intent, and enforces the `Σ = 0` per-currency invariant —
 * throwing [UnbalancedTransactionException] and writing nothing on failure.
 *
 * Only *system* accounts are created on demand: card accounts are created with
 * their facade, so nothing here has to guess whether one exists.
 */
class LedgerEntryWriter(
    private val entryDao: EntryDao,
    private val accountDao: AccountDao,
    private val creditCardDao: CreditCardDao,
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
    suspend fun rewriteEntries(transactionId: Long, legs: List<TransactionLeg>) {
        entryDao.deleteByTransactionId(transactionId)
        writeEntries(transactionId, legs)
    }

    suspend fun writeEntries(transactionId: Long, legs: List<TransactionLeg>) {
        val entries = buildList {
            legs.forEach { leg ->
                add(
                    EntryEntity(
                        transactionId = transactionId,
                        accountId = realAccountId(leg),
                        amount = leg.ledgerAmount(),
                        currency = BASE_CURRENCY,
                        // Only the credit-card (LIABILITY) leg carries the invoice's
                        // dimension — its sub-ledger. A payment's account leg also
                        // references the card but must not carry it, or the two legs
                        // would cancel the sub-ledger out.
                        dimensionId = leg.invoice?.dimensionId
                            ?.takeIf { leg.target == TransactionTarget.CREDIT_CARD },
                    )
                )
            }
            // Single-leg transactions need a synthesized contra leg to balance.
            if (legs.size == 1) {
                val leg = legs.first()
                add(
                    EntryEntity(
                        transactionId = transactionId,
                        accountId = contraAccountId(leg),
                        amount = -leg.ledgerAmount(),
                        currency = BASE_CURRENCY,
                        // A nominal leg is classified by the category's dimension.
                        // No category means genuinely unclassified — there is no
                        // bucket account and no bucket dimension standing in for it.
                        dimensionId = leg.category?.dimensionId,
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
     * Only the **monetary** facades are checked. Closing an ASSET/LIABILITY
     * requires a zero balance, so a new entry there strands money; a category
     * closes at any balance, so writing to it breaks nothing the ledger promised.
     * Keeping an archived category out of a *new* transaction is a selector's job
     * (it lists open ones), not an invariant — and enforcing it here is what made
     * editing an old transaction fail for a reason the ledger never had.
     */
    private suspend fun Long.orRejectIfClosed(facade: ClosedFacade): Long = also { accountId ->
        if (accountDao.getAccountById(accountId)?.isArchived == true) {
            throw ClosedAccountException(LedgerError.ClosedAccount(facade))
        }
    }

    private suspend fun realAccountId(leg: TransactionLeg): Long = when (leg.target) {
        TransactionTarget.ACCOUNT ->
            (leg.account?.id ?: throw UnbalancedTransactionException(LedgerError.Unbalanced))
                .orRejectIfClosed(ClosedFacade.ACCOUNT)

        TransactionTarget.CREDIT_CARD ->
            cardAccountId(leg.creditCard ?: throw UnbalancedTransactionException(LedgerError.Unbalanced))
                .orRejectIfClosed(ClosedFacade.CREDIT_CARD)
    }

    /**
     * The account the synthesized contra leg posts to: reconciliation for an
     * adjustment, otherwise one of the two nominal accounts.
     *
     * Which nominal is chosen comes from [Category.type] — feature state, not a
     * ledger derivation. **This is the documented exception to the project's
     * Derivation Rule** (design D4): "this is an expense category" is the user's
     * declaration at creation time and nothing derives it. It used to be encoded as
     * the type of the category's own account, which made it look derived; the state
     * only moved home. With no category the *leg's* own type decides, which is the
     * same question asked of the only source left.
     */
    private suspend fun contraAccountId(leg: TransactionLeg): Long = when (leg.type) {
        TransactionType.ADJUSTMENT ->
            ensureSystemAccount(SystemAccount.RECONCILIATION, AccountEntity.Type.EQUITY)

        TransactionType.EXPENSE, TransactionType.INCOME -> when (leg.category?.type ?: leg.type.asCategoryType()) {
            Category.Type.EXPENSE -> ensureSystemAccount(SystemAccount.EXPENSES, AccountEntity.Type.EXPENSE)
            Category.Type.INCOME -> ensureSystemAccount(SystemAccount.INCOMES, AccountEntity.Type.INCOME)
        }
    }

    private fun TransactionType.asCategoryType(): Category.Type =
        if (isIncome) Category.Type.INCOME else Category.Type.EXPENSE

    private suspend fun cardAccountId(creditCard: CreditCard): Long =
        creditCardDao.getCreditCardById(creditCard.id)?.accountId
            ?: throw UnbalancedTransactionException(LedgerError.Unbalanced)

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
