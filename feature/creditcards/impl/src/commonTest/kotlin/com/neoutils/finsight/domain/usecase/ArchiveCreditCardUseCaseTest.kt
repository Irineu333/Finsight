package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.IAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Archiving a card closes its `LIABILITY` account through the single owner of that
 * decision, [ArchiveAccountUseCase] — it never deletes and never touches the facade
 * row. A card whose account is gone is refused with `NOT_FOUND`.
 */
class ArchiveCreditCardUseCaseTest {

    private val cardAccount = Account(id = 42L, name = "Card", type = AccountType.LIABILITY)
    private val card = CreditCard(
        id = 1L, name = "Card", limit = 1000.0, closingDay = 10, dueDay = 20, accountId = 42L,
    )

    @Test
    fun `archiving delegates the card's account to ArchiveAccountUseCase`() = runTest {
        val archive = RecordingArchiveAccountUseCase()
        val useCase = ArchiveCreditCardUseCase(
            accountRepository = FakeAccountRepository(cardAccount),
            archiveAccountUseCase = archive,
        )

        val result = useCase(card)

        assertTrue(result.isRight())
        assertEquals(listOf(cardAccount), archive.archived)
    }

    @Test
    fun `a card whose account is gone is refused`() = runTest {
        val archive = RecordingArchiveAccountUseCase()
        val useCase = ArchiveCreditCardUseCase(
            accountRepository = FakeAccountRepository(account = null),
            archiveAccountUseCase = archive,
        )

        val result = useCase(card)

        assertEquals(AccountError.NOT_FOUND, (result.leftOrNull() as AccountException).error)
        assertTrue(archive.archived.isEmpty(), "the account is never closed")
    }
}

private class RecordingArchiveAccountUseCase : ArchiveAccountUseCase {
    val archived = mutableListOf<Account>()
    override suspend fun invoke(account: Account): Either<Throwable, Unit> {
        archived += account
        return Unit.right()
    }
}

private class FakeAccountRepository(private val account: Account?) : IAccountRepository {
    override suspend fun getAccountById(accountId: Long): Account? = account
    override fun observeAllAccounts(): Flow<List<Account>> = throw NotImplementedError()
    override suspend fun getAllAccounts(): List<Account> = throw NotImplementedError()
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = throw NotImplementedError()
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = throw NotImplementedError()
    override suspend fun getAllLedgerAccounts(): List<Account> = throw NotImplementedError()
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = throw NotImplementedError()
    override fun observeAccountById(accountId: Long): Flow<Account?> = throw NotImplementedError()
    override suspend fun getDefaultAccount(): Account? = throw NotImplementedError()
    override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
    override suspend fun getAccountCount(): Int = throw NotImplementedError()
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()
}
