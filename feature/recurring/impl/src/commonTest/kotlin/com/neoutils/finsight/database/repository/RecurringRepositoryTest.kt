package com.neoutils.finsight.database.repository

import app.cash.turbine.test
import com.neoutils.finsight.database.dao.RecurringDao
import com.neoutils.finsight.database.entity.RecurringEntity
import com.neoutils.finsight.database.mapper.RecurringMapper
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecurringRepositoryTest {

    private val recurringFlow = MutableSharedFlow<List<RecurringEntity>>(replay = 1)

    private val dao = object : RecurringDao {
        override fun observeAll(): Flow<List<RecurringEntity>> = recurringFlow
        override suspend fun getAll(): List<RecurringEntity> = throw NotImplementedError()
        override suspend fun insert(entity: RecurringEntity): Long = throw NotImplementedError()
        override suspend fun update(entity: RecurringEntity) = throw NotImplementedError()
        override suspend fun delete(entity: RecurringEntity) = throw NotImplementedError()
    }

    private val categoryRepository = object : ICategoryRepository {
        override fun observeAllCategories(): Flow<List<Category>> = flowOf(emptyList())
        override suspend fun getAllCategories(): List<Category> = throw NotImplementedError()
        override suspend fun getAllCategoriesIncludingClosed(): List<Category> = getAllCategories()
        override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = observeAllCategories()
        override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
        override suspend fun getCategoryById(id: Long): Category? = throw NotImplementedError()
        override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
        override suspend fun insert(category: Category) = throw NotImplementedError()
        override suspend fun update(category: Category) = throw NotImplementedError()
        override suspend fun delete(category: Category) = throw NotImplementedError()
    }

    private val accountRepository = object : IAccountRepository {
        override fun observeAllAccounts(): Flow<List<Account>> = flowOf(emptyList())
        override suspend fun getAllAccounts(): List<Account> = throw NotImplementedError()
        override suspend fun getAllLedgerAccounts(): List<Account> = throw NotImplementedError()
        override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(emptyList())
        override suspend fun getAccountById(accountId: Long): Account? = throw NotImplementedError()
        override fun observeAccountById(accountId: Long): Flow<Account?> = throw NotImplementedError()
        override suspend fun getDefaultAccount(): Account? = throw NotImplementedError()
        override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
        override suspend fun getAccountCount(): Int = throw NotImplementedError()
        override suspend fun insert(account: Account): Long = throw NotImplementedError()
        override suspend fun update(account: Account) = throw NotImplementedError()
        override suspend fun delete(account: Account) = throw NotImplementedError()
    }

    private val creditCardRepository = object : ICreditCardRepository {
        override fun observeAllCreditCards(): Flow<List<CreditCard>> = flowOf(emptyList())
        override suspend fun getAllCreditCards(): List<CreditCard> = throw NotImplementedError()
        override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = getAllCreditCards()
        override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = observeAllCreditCards()
        override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = throw NotImplementedError()
        override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
        override suspend fun insert(creditCard: CreditCard): Long = throw NotImplementedError()
        override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
        override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
    }

    private val repository = RecurringRepository(
        dao = dao,
        mapper = RecurringMapper(),
        categoryRepository = categoryRepository,
        accountRepository = accountRepository,
        creditCardRepository = creditCardRepository,
    )

    private fun entity(id: Long, amount: Double) = RecurringEntity(
        id = id,
        type = RecurringEntity.Type.EXPENSE,
        amount = amount,
        title = "Rec $id",
        dayOfMonth = 5,
        categoryId = null,
        accountId = null,
        creditCardId = null,
        createdAt = 0L,
    )

    @Test
    fun emitsEntityReemitsOnChangeAndNullOnRemoval() = runTest {
        repository.observeRecurringById(1L).test {
            recurringFlow.emit(listOf(entity(id = 1L, amount = 100.0)))
            assertEquals(100.0, awaitItem()?.amount)

            // change: same id, new amount → re-emits updated entity
            recurringFlow.emit(listOf(entity(id = 1L, amount = 250.0)))
            assertEquals(250.0, awaitItem()?.amount)

            // removal → emits null
            recurringFlow.emit(emptyList())
            assertNull(awaitItem())
        }
    }

    @Test
    fun emitsNullWhenIdNotPresent() = runTest {
        repository.observeRecurringById(99L).test {
            recurringFlow.emit(listOf(entity(id = 1L, amount = 100.0)))
            assertNull(awaitItem())
        }
    }
}
