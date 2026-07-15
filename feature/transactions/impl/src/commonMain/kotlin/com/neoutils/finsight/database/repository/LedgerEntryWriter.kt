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
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.signedCents

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
    fun validate(transactions: List<Transaction>) {
        if (transactions.size < 2) return
        val total = transactions.sumOf { it.signedCents() }
        if (total != 0L) throw UnbalancedOperationException(LedgerError.Unbalanced)
    }

    /** Rebuilds the entries of [operationId] from its (edited) legs. */
    suspend fun rewriteEntries(operationId: Long, transactions: List<Transaction>) {
        entryDao.deleteByOperationId(operationId)
        writeEntries(operationId, transactions)
    }

    suspend fun writeEntries(operationId: Long, transactions: List<Transaction>) {
        val entries = buildList {
            transactions.forEach { transaction ->
                add(
                    EntryEntity(
                        operationId = operationId,
                        accountId = realAccountId(transaction),
                        amount = transaction.signedCents(),
                        currency = BASE_CURRENCY,
                        // Only the credit-card leg carries the invoice (its sub-ledger).
                        invoiceId = transaction.invoice?.id,
                    )
                )
            }
            // Single-leg operations need a synthesized contra leg to balance.
            if (transactions.size == 1) {
                val transaction = transactions.first()
                add(
                    EntryEntity(
                        operationId = operationId,
                        accountId = contraAccountId(transaction),
                        amount = -transaction.signedCents(),
                        currency = BASE_CURRENCY,
                    )
                )
            }
        }

        if (entries.sumOf { it.amount } != 0L) {
            throw UnbalancedOperationException(LedgerError.Unbalanced)
        }

        entryDao.insertAll(entries)
    }

    private suspend fun realAccountId(transaction: Transaction): Long = when (transaction.target) {
        Transaction.Target.ACCOUNT ->
            transaction.account?.id ?: throw UnbalancedOperationException(LedgerError.Unbalanced)

        Transaction.Target.CREDIT_CARD ->
            ensureCardAccount(transaction.creditCard ?: throw UnbalancedOperationException(LedgerError.Unbalanced))
    }

    private suspend fun contraAccountId(transaction: Transaction): Long = when (transaction.type) {
        Transaction.Type.ADJUSTMENT ->
            ensureSystemAccount(SystemAccount.RECONCILIATION, AccountEntity.Type.EQUITY)

        Transaction.Type.EXPENSE ->
            transaction.category?.let { ensureCategoryAccount(it) }
                ?: ensureSystemAccount(SystemAccount.UNCATEGORIZED_EXPENSE, AccountEntity.Type.EXPENSE)

        Transaction.Type.INCOME ->
            transaction.category?.let { ensureCategoryAccount(it) }
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
}
