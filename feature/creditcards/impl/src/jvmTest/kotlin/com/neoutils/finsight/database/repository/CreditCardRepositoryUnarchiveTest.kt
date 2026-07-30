package com.neoutils.finsight.database.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.mapper.CreditCardMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `unarchive` reopens the card's chart-of-accounts row (`AccountDao.reopen`) — the
 * inverse of `close`, against a real in-memory Room database.
 */
class CreditCardRepositoryUnarchiveTest {

    /** The base currency a real app resolves from the locale; here it is simply stated. */
    private val baseCurrency = object : IBaseCurrencyRepository {
        override fun observe(): StateFlow<String> = MutableStateFlow("BRL")
        override suspend fun set(currency: String) = Unit
    }


    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @AfterTest fun tearDown() = db.close()

    private val repository = CreditCardRepository(
        database = db,
        dao = db.creditCardDao(),
        accountDao = db.accountDao(),
        mapper = CreditCardMapper(),
    )


    @Test
    fun `unarchive flips the account's archived flag back off`() = runTest {
        val accountId = db.accountDao().insert(
            AccountEntity(currency = "BRL", name = "Card", type = AccountEntity.Type.LIABILITY),
        )
        db.accountDao().close(accountId)
        assertTrue(db.accountDao().getAccountById(accountId)!!.isArchived)

        repository.unarchive(accountId)

        assertFalse(db.accountDao().getAccountById(accountId)!!.isArchived)
    }
}
