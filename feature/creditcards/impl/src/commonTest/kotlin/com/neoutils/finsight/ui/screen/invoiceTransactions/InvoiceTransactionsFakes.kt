package com.neoutils.finsight.ui.screen.invoiceTransactions

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.usecase.UnarchiveCreditCardUseCase
import com.neoutils.finsight.domain.model.AccountType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The fakes the invoice-transactions tests share. They answer just enough for the
 * ViewModel to emit: no figure and no read here is under test on its own.
 */
internal class FakeCreditCardRepository(private val card: CreditCard) : ICreditCardRepository {
    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = MutableStateFlow(card)
    override fun observeAllCreditCards(): Flow<List<CreditCard>> = throw NotImplementedError()
    override suspend fun getAllCreditCards(): List<CreditCard> = throw NotImplementedError()
    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = getAllCreditCards()
    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = observeAllCreditCards()
    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = card
    override suspend fun insert(creditCard: CreditCard): Long = throw NotImplementedError()
    override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun unarchive(accountId: Long) = throw NotImplementedError()
}

internal class FakeInvoiceRepository(private val invoices: List<Invoice>) : IInvoiceRepository {
    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = MutableStateFlow(invoices)
    override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> = invoices
    override suspend fun getInvoiceById(id: Long): Invoice? = invoices.firstOrNull { it.id == id }
    override fun observeAllInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeInvoiceById(dimensionId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeUnpaidInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
    override suspend fun getAllInvoices(): List<Invoice> = throw NotImplementedError()
    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> = throw NotImplementedError()
    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = throw NotImplementedError()
    override suspend fun insert(invoice: Invoice): Invoice = throw NotImplementedError()
    override suspend fun update(invoice: Invoice) = throw NotImplementedError()
    override suspend fun deleteById(id: Long) = throw NotImplementedError()
}

internal class FakeTransactionRepository(private val transactions: List<Transaction>) : ITransactionRepository {
    override fun observeTransactionsBy(date: LocalDate?, dimensionId: Long?, accountId: Long?): Flow<List<Transaction>> = MutableStateFlow(transactions)
    override fun observeAllTransactions(): Flow<List<Transaction>> = throw NotImplementedError()
    override fun observeTransactionById(id: Long): Flow<Transaction?> = throw NotImplementedError()
    override suspend fun getAllTransactions(): List<Transaction> = throw NotImplementedError()
    override suspend fun getTransactionById(id: Long): Transaction? = throw NotImplementedError()
    override suspend fun createTransaction(intent: TransactionIntent): Transaction = throw NotImplementedError()
    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> = throw NotImplementedError()
    override suspend fun updateTransaction(id: Long, title: String?, date: LocalDate, leg: TransactionLeg, contra: ContraLeg?) = throw NotImplementedError()
    override suspend fun deleteTransactionsByIds(ids: List<Long>) = ids.forEach { deleteTransactionById(it) }

    override suspend fun deleteTransactionById(id: Long) = throw NotImplementedError()
}

internal class FakeCategoryRepository : ICategoryRepository {
    override fun observeAllCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())
    override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
    override suspend fun getAllCategories(): List<Category> = throw NotImplementedError()
    override suspend fun getAllCategoriesIncludingClosed(): List<Category> = getAllCategories()
    override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = observeAllCategories()
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
    override suspend fun getCategoryById(id: Long): Category? = throw NotImplementedError()
    override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? = null
    override suspend fun archive(id: Long) = Unit
    override suspend fun unarchive(id: Long) = Unit
    override suspend fun existsByName(name: String, ignoreId: Long): Boolean = false

    override suspend fun insert(category: Category) = throw NotImplementedError()
    override suspend fun insertAll(categories: List<Category>) = throw NotImplementedError()
    override suspend fun update(category: Category) = throw NotImplementedError()
    override suspend fun delete(category: Category) = throw NotImplementedError()
}

internal class FakeEntryRepository(
    private val owedByInvoiceId: Map<Long, Double>,
    private val flowsByInvoiceId: Map<Long, com.neoutils.finsight.domain.repository.DimensionFlows> = emptyMap(),
) : IEntryRepository {
    override suspend fun dimensionOwed(dimensionId: Long): Double = owedByInvoiceId[dimensionId] ?: 0.0
    override suspend fun dimensionFlows(dimensionId: Long): com.neoutils.finsight.domain.repository.DimensionFlows =
        flowsByInvoiceId[dimensionId] ?: com.neoutils.finsight.domain.repository.DimensionFlows(0.0, 0.0, 0.0)
    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
    override suspend fun naturalBalanceUpTo(target: YearMonth, type: com.neoutils.finsight.domain.model.AccountType): Double = throw NotImplementedError()
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()
    override suspend fun liabilityMonthFlows(month: YearMonth): com.neoutils.finsight.domain.repository.LiabilityMonthFlows = throw NotImplementedError()
    override suspend fun assetMonthFlows(month: YearMonth): com.neoutils.finsight.domain.repository.AssetMonthFlows = throw NotImplementedError()
    override suspend fun netWorth(): Double = throw NotImplementedError()
    override suspend fun totalsByDimension(nominalType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScope(nominalType: AccountType, scopeDimensionIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun scopeStats(scopeAccountIds: List<Long>, startDate: LocalDate, endDate: LocalDate): com.neoutils.finsight.domain.repository.ScopeStats = throw NotImplementedError()
}

/** No installment badge is under test here; the list only needs the read to answer. */
internal object NoInstallments : IInstallmentRepository {
    override fun observeAllInstallments(): Flow<List<Installment>> = flowOf(emptyList())
    override suspend fun getAllInstallments(): List<Installment> = emptyList()
    override suspend fun getInstallmentById(id: Long): Installment? = null
    override suspend fun createInstallment(count: Int, totalAmount: Double): Long = throw NotImplementedError()
    override suspend fun updateInstallment(id: Long, count: Int, totalAmount: Double) = throw NotImplementedError()
    override suspend fun deleteInstallmentById(id: Long) = throw NotImplementedError()
}

internal object NoCrashlytics : Crashlytics {
    override fun setUserId(id: String?) = Unit
    override fun recordException(e: Throwable) = Unit
}

/** The retire action is not what this test characterizes; no template points at the card. */
internal object NoRecurring : IRecurringRepository {
    override fun observeAllRecurring(): Flow<List<Recurring>> = flowOf(emptyList())
    override fun observeRecurringById(id: Long): Flow<Recurring?> = throw NotImplementedError()
    override suspend fun getRecurringById(id: Long): Recurring? = null
    override suspend fun hasRecurringForAccount(accountId: Long): Boolean = false
    override suspend fun hasRecurringForCreditCard(creditCardId: Long): Boolean = false
    override suspend fun hasRecurringForCategory(categoryId: Long): Boolean = false
    override suspend fun hasTransactionForRecurring(recurringId: Long): Boolean = false
    override suspend fun insert(recurring: Recurring) = throw NotImplementedError()
    override suspend fun update(recurring: Recurring) = throw NotImplementedError()
    override suspend fun delete(recurring: Recurring) = throw NotImplementedError()
}
