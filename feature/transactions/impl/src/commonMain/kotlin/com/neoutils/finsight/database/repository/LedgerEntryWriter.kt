package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.database.dao.CategoryDao
import com.neoutils.finsight.database.dao.CreditCardDao
import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.ClosedFacade
import com.neoutils.finsight.domain.error.LedgerError
import com.neoutils.finsight.domain.error.UnbalancedTransactionException
import com.neoutils.finsight.domain.model.BASE_CURRENCY
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
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
 * Only *system* accounts are still created on demand: category and card accounts
 * are created with their facade, so nothing here has to guess whether one exists.
 */
class LedgerEntryWriter(
    private val entryDao: EntryDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val creditCardDao: CreditCardDao,
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
                        // Only the credit-card (LIABILITY) leg carries the invoice — its
                        // sub-ledger. A payment's account leg also references the card but
                        // must not tag the invoice, or the two legs would cancel it out.
                        invoiceId = leg.invoice?.id
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

        entryDao.insertAll(entries)
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

    private suspend fun contraAccountId(leg: TransactionLeg): Long = when (leg.type) {
        TransactionType.ADJUSTMENT ->
            ensureSystemAccount(SystemAccount.RECONCILIATION, AccountEntity.Type.EQUITY)

        TransactionType.EXPENSE ->
            leg.category?.let { categoryAccountId(it) }
                ?: ensureSystemAccount(SystemAccount.UNCATEGORIZED_EXPENSE, AccountEntity.Type.EXPENSE)

        TransactionType.INCOME ->
            leg.category?.let { categoryAccountId(it) }
                ?: ensureSystemAccount(SystemAccount.UNCATEGORIZED_INCOME, AccountEntity.Type.INCOME)
    }

    /**
     * The category's account, looked up — never created. Categories and cards are
     * created together with their account (see `CategoryRepository`), so by the
     * time anything is spent there the row exists. This is the difference between
     * a facade that *has* an account and one that might.
     */
    private suspend fun categoryAccountId(category: Category): Long =
        categoryDao.getCategoryById(category.id)?.accountId
            ?: throw UnbalancedTransactionException(LedgerError.Unbalanced)

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
