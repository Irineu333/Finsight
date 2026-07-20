package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.database.dao.CategoryDao
import com.neoutils.finsight.database.dao.CreditCardDao
import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.domain.error.LedgerError
import com.neoutils.finsight.domain.error.UnbalancedOperationException
import com.neoutils.finsight.domain.model.BASE_CURRENCY
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.SystemAccount
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.OperationLeg
import kotlin.math.roundToLong

/**
 * The single write-boundary that turns the legs of an operation into balanced
 * double-entry [EntryEntity] rows.
 *
 * It ensures the chart-of-accounts row for each category and card exists
 * (creating it on demand, keeping the facade projection consistent), synthesizes
 * the contra leg for single-leg operations, and enforces the `Σ = 0` per-currency
 * invariant — throwing [UnbalancedOperationException] and writing nothing on
 * failure.
 */
class LedgerEntryWriter(
    private val entryDao: EntryDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val creditCardDao: CreditCardDao,
) {

    /**
     * Validates the balance invariant on the raw legs before any row is written,
     * so an unbalanced multi-leg operation is rejected without side effects.
     */
    fun validate(legs: List<OperationLeg>) {
        if (legs.size < 2) return
        val total = legs.sumOf { it.signedCents() }
        if (total != 0L) throw UnbalancedOperationException(LedgerError.Unbalanced)
    }

    /** Rebuilds the entries of [transactionId] from its (edited) legs. */
    suspend fun rewriteEntries(transactionId: Long, legs: List<OperationLeg>) {
        entryDao.deleteByOperationId(transactionId)
        writeEntries(transactionId, legs)
    }

    suspend fun writeEntries(transactionId: Long, legs: List<OperationLeg>) {
        val entries = buildList {
            legs.forEach { leg ->
                add(
                    EntryEntity(
                        transactionId = transactionId,
                        accountId = realAccountId(leg),
                        amount = leg.signedCents(),
                        currency = BASE_CURRENCY,
                        // Only the credit-card (LIABILITY) leg carries the invoice — its
                        // sub-ledger. A payment's account leg also references the card but
                        // must not tag the invoice, or the two legs would cancel it out.
                        invoiceId = leg.invoice?.id
                            ?.takeIf { leg.target == TransactionTarget.CREDIT_CARD },
                    )
                )
            }
            // Single-leg operations need a synthesized contra leg to balance.
            if (legs.size == 1) {
                val leg = legs.first()
                add(
                    EntryEntity(
                        transactionId = transactionId,
                        accountId = contraAccountId(leg),
                        amount = -leg.signedCents(),
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
            throw UnbalancedOperationException(LedgerError.Unbalanced)
        }

        entryDao.insertAll(entries)
    }

    private suspend fun realAccountId(leg: OperationLeg): Long = when (leg.target) {
        TransactionTarget.ACCOUNT ->
            leg.account?.id ?: throw UnbalancedOperationException(LedgerError.Unbalanced)

        TransactionTarget.CREDIT_CARD ->
            ensureCardAccount(leg.creditCard ?: throw UnbalancedOperationException(LedgerError.Unbalanced))
    }

    private suspend fun contraAccountId(leg: OperationLeg): Long = when (leg.type) {
        TransactionType.ADJUSTMENT ->
            ensureSystemAccount(SystemAccount.RECONCILIATION, AccountEntity.Type.EQUITY)

        TransactionType.EXPENSE ->
            leg.category?.let { ensureCategoryAccount(it) }
                ?: ensureSystemAccount(SystemAccount.UNCATEGORIZED_EXPENSE, AccountEntity.Type.EXPENSE)

        TransactionType.INCOME ->
            leg.category?.let { ensureCategoryAccount(it) }
                ?: ensureSystemAccount(SystemAccount.UNCATEGORIZED_INCOME, AccountEntity.Type.INCOME)
    }

    private suspend fun ensureCategoryAccount(category: Category): Long {
        val entity = categoryDao.getCategoryById(category.id)
            ?: throw UnbalancedOperationException(LedgerError.Unbalanced)
        entity.accountId?.let { return it }
        val accountId = accountDao.insert(
            AccountEntity(
                name = category.name,
                type = when (category.type) {
                    Category.Type.INCOME -> AccountEntity.Type.INCOME
                    Category.Type.EXPENSE -> AccountEntity.Type.EXPENSE
                },
                currency = BASE_CURRENCY,
                iconKey = entity.iconKey,
            )
        )
        categoryDao.update(entity.copy(accountId = accountId))
        return accountId
    }

    private suspend fun ensureCardAccount(creditCard: CreditCard): Long {
        val entity = creditCardDao.getCreditCardById(creditCard.id)
            ?: throw UnbalancedOperationException(LedgerError.Unbalanced)
        entity.accountId?.let { return it }
        val accountId = accountDao.insert(
            AccountEntity(
                name = creditCard.name,
                type = AccountEntity.Type.LIABILITY,
                currency = BASE_CURRENCY,
                iconKey = entity.iconKey,
            )
        )
        creditCardDao.update(entity.copy(accountId = accountId))
        return accountId
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
    private fun OperationLeg.signedCents(): Long {
        val cents = (amount * 100).roundToLong()
        return when (type) {
            TransactionType.EXPENSE -> -cents
            TransactionType.INCOME -> cents
            TransactionType.ADJUSTMENT -> cents
        }
    }
}
