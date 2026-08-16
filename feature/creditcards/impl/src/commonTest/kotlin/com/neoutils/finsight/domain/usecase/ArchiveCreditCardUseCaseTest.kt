package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.error.CreditCardError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.exception.CreditCardException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

    private val cardAccount = Account(id = 42L, name = "Card", type = AccountType.LIABILITY, currency = "BRL")
    private val card = CreditCard(
        id = 1L, name = "Card", limit = 1000.0, closingDay = 10, dueDay = 20, accountId = 42L,
    )

    @Test
    fun `archiving delegates the card's account to ArchiveAccountUseCase`() = runTest {
        val archive = RecordingArchiveAccountUseCase()
        val useCase = ArchiveCreditCardUseCaseImpl(
            creditCardRepository = FakeCreditCardRepository(card),
            accountRepository = FakeAccountRepository(cardAccount),
            archiveAccountUseCase = archive,
        )

        val result = useCase(card)

        assertTrue(result.isRight())
        assertEquals(listOf(cardAccount.id), archive.archived)
    }

    @Test
    fun `a card whose account is gone is refused`() = runTest {
        val archive = RecordingArchiveAccountUseCase()
        val useCase = ArchiveCreditCardUseCaseImpl(
            creditCardRepository = FakeCreditCardRepository(card),
            accountRepository = FakeAccountRepository(account = null),
            archiveAccountUseCase = archive,
        )

        val result = useCase(card)

        assertEquals(AccountError.NOT_FOUND, (result.leftOrNull() as AccountException).error)
        assertTrue(archive.archived.isEmpty(), "the account is never closed")
    }

    @Test
    fun `archiving a card that does not exist is refused and nothing is closed`() = runTest {
        val archive = RecordingArchiveAccountUseCase()
        val useCase = ArchiveCreditCardUseCaseImpl(
            creditCardRepository = FakeCreditCardRepository(card),
            accountRepository = FakeAccountRepository(cardAccount),
            archiveAccountUseCase = archive,
        )

        val result = useCase(404L)

        assertEquals(
            CreditCardError.NOT_FOUND,
            (result.leftOrNull() as CreditCardException).error,
        )
        assertTrue(archive.archived.isEmpty(), "the account is never closed")
    }

    @Test
    fun `archiving by id and by card are the same operation`() = runTest {
        val byId = RecordingArchiveAccountUseCase()
        val byCard = RecordingArchiveAccountUseCase()

        val fromId = ArchiveCreditCardUseCaseImpl(
            creditCardRepository = FakeCreditCardRepository(card),
            accountRepository = FakeAccountRepository(cardAccount),
            archiveAccountUseCase = byId,
        )(card.id)

        val fromCard = ArchiveCreditCardUseCaseImpl(
            creditCardRepository = FakeCreditCardRepository(card),
            accountRepository = FakeAccountRepository(cardAccount),
            archiveAccountUseCase = byCard,
        )(card)

        assertEquals(fromId.isRight(), fromCard.isRight())
        assertEquals(byId.archived, byCard.archived)
    }
}

private class FakeCreditCardRepository(
    private vararg val cards: CreditCard,
) : ICreditCardRepository {
    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? =
        cards.firstOrNull { it.id == creditCardId }
    override suspend fun getAllCreditCards(): List<CreditCard> = cards.toList()
    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = cards.toList()
    override fun observeAllCreditCards(): Flow<List<CreditCard>> = throw NotImplementedError()
    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = throw NotImplementedError()
    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
    override suspend fun insert(creditCard: CreditCard, currency: String): Long = throw NotImplementedError()
    override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun unarchive(accountId: Long) = throw NotImplementedError()
    override suspend fun currencyForNewCard(): String = throw NotImplementedError()
}

private class RecordingArchiveAccountUseCase : ArchiveAccountUseCase {
    val archived = mutableListOf<Long>()
    override suspend fun invoke(accountId: Long): Either<Throwable, Unit> {
        archived += accountId
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
    override suspend fun hasYieldingAccount(): Boolean = false
    override suspend fun getAccountCount(): Int = throw NotImplementedError()
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()
}
