package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The account use cases are identified by **id**, and that form is the one that
 * carries the rule.
 *
 * Two properties are asserted of each, and they are the pair that makes the second
 * form safe to offer: an identity matching nothing is refused with `NOT_FOUND` before
 * anything is written, and the two forms of one use case produce the same result for
 * the same identity — because the one taking the account only extracts its id.
 *
 * Resolving at execution rather than trusting what the caller holds is what the last
 * test is about: an account loaded by a screen is a reading that can already be out of
 * date, and the guard has to read the account as it is when the action runs.
 */
class AccountUseCaseIdentityTest {

    private val account = Account(id = 1, name = "Wallet", type = AccountType.ASSET, currency = "BRL")
    private val absent = Account(id = 404, name = "Gone", type = AccountType.ASSET, currency = "BRL")
    private val date = LocalDate(2026, 1, 10)

    @Test
    fun `archiving an account that does not exist is refused and nothing is closed`() = runTest {
        val dao = RecordingAccountDao()
        val useCase = ArchiveAccountUseCaseImpl(
            accountRepository = RecordingAccountRepository(account),
            accountDao = dao,
            entryRepository = FakeEntries(hasEntries = false, balance = 0.0),
        )

        val error = assertIs<AccountException>(useCase(absent.id).leftOrNull())

        assertEquals(AccountError.NOT_FOUND, error.error)
        assertTrue(dao.closed.isEmpty(), "nothing may be closed")
    }

    @Test
    fun `archiving by id and by account are the same operation`() = runTest {
        val byId = RecordingAccountDao()
        val byAccount = RecordingAccountDao()

        val fromId = ArchiveAccountUseCaseImpl(
            accountRepository = RecordingAccountRepository(account),
            accountDao = byId,
            entryRepository = FakeEntries(hasEntries = true, balance = 0.0),
        )(account.id)

        val fromAccount = ArchiveAccountUseCaseImpl(
            accountRepository = RecordingAccountRepository(account),
            accountDao = byAccount,
            entryRepository = FakeEntries(hasEntries = true, balance = 0.0),
        )(account)

        assertEquals(fromId.isRight(), fromAccount.isRight())
        assertEquals(byId.closed, byAccount.closed)
    }

    @Test
    fun `deleting an account that does not exist is refused and nothing is removed`() = runTest {
        val repository = RecordingAccountRepository(account)
        val useCase = DeleteAccountUseCaseImpl(
            accountRepository = repository,
            entryRepository = FakeEntries(hasEntries = false),
            recurringRepository = FakeRecurring(),
        )

        val error = assertIs<AccountException>(useCase(absent.id).leftOrNull())

        assertEquals(AccountError.NOT_FOUND, error.error)
        assertTrue(repository.deleted.isEmpty(), "nothing may be removed")
    }

    @Test
    fun `deleting by id and by account are the same operation`() = runTest {
        val byId = RecordingAccountRepository(account)
        val byAccount = RecordingAccountRepository(account)

        val fromId = DeleteAccountUseCaseImpl(
            accountRepository = byId,
            entryRepository = FakeEntries(hasEntries = false),
            recurringRepository = FakeRecurring(),
        )(account.id)

        val fromAccount = DeleteAccountUseCaseImpl(
            accountRepository = byAccount,
            entryRepository = FakeEntries(hasEntries = false),
            recurringRepository = FakeRecurring(),
        )(account)

        assertEquals(fromId.isRight(), fromAccount.isRight())
        assertEquals(byId.deleted, byAccount.deleted)
    }

    @Test
    fun `unarchiving an account that does not exist is refused and nothing is reopened`() = runTest {
        val repository = RecordingAccountRepository(account)

        val error = assertIs<AccountException>(
            UnarchiveAccountUseCaseImpl(repository)(absent.id).leftOrNull()
        )

        assertEquals(AccountError.NOT_FOUND, error.error)
        assertTrue(repository.reopened.isEmpty(), "nothing may be reopened")
    }

    @Test
    fun `unarchiving by id and by account are the same operation`() = runTest {
        val archived = account.copy(isArchived = true)
        val byId = RecordingAccountRepository(archived)
        val byAccount = RecordingAccountRepository(archived)

        val fromId = UnarchiveAccountUseCaseImpl(byId)(archived.id)
        val fromAccount = UnarchiveAccountUseCaseImpl(byAccount)(archived)

        assertEquals(fromId.isRight(), fromAccount.isRight())
        assertEquals(byId.reopened, byAccount.reopened)
    }

    @Test
    fun `electing a default that does not exist is refused and no account changes`() = runTest {
        // The election demotes whoever holds the role, so an unresolvable identity would
        // otherwise leave the app with no default at all.
        val repository = RecordingAccountRepository(account.copy(isDefault = true))

        val error = assertIs<AccountException>(
            SetDefaultAccountUseCaseImpl(repository)(absent.id).leftOrNull()
        )

        assertEquals(AccountError.NOT_FOUND, error.error)
        assertTrue(repository.updated.isEmpty(), "the incumbent keeps the role")
    }

    @Test
    fun `adjusting the balance of an account that does not exist is refused`() = runTest {
        val ledger = LedgerStore(account)
        val transactions = FakeTransactionRepository(ledger)
        val useCase = AdjustBalanceUseCaseImpl(
            accountRepository = KnownAccounts(account),
            transactionRepository = transactions,
            calculateBalanceUseCase = CalculateBalanceUseCase(FakeEntryRepository(ledger)),
        )

        val error = assertIs<AccountException>(
            useCase(targetBalance = 100.0, adjustmentDate = date, accountId = absent.id).leftOrNull()
        )

        assertEquals(AccountError.NOT_FOUND, error.error)
        assertTrue(ledger.entriesByTransaction.isEmpty(), "nothing may be written")
    }

    @Test
    fun `adjusting by id and by account are the same operation`() = runTest {
        val byIdLedger = LedgerStore(account)
        val byAccountLedger = LedgerStore(account)

        AdjustBalanceUseCaseImpl(
            accountRepository = KnownAccounts(account),
            transactionRepository = FakeTransactionRepository(byIdLedger),
            calculateBalanceUseCase = CalculateBalanceUseCase(FakeEntryRepository(byIdLedger)),
        )(targetBalance = 100.0, adjustmentDate = date, accountId = account.id)

        AdjustBalanceUseCaseImpl(
            accountRepository = KnownAccounts(account),
            transactionRepository = FakeTransactionRepository(byAccountLedger),
            calculateBalanceUseCase = CalculateBalanceUseCase(FakeEntryRepository(byAccountLedger)),
        )(targetBalance = 100.0, adjustmentDate = date, account = account)

        assertEquals(byIdLedger.adjustmentsByDate(), byAccountLedger.adjustmentsByDate())
        assertEquals(100.0, byIdLedger.accountBalance())
    }

    @Test
    fun `the account is read as it is when the action runs, not as the caller holds it`() = runTest {
        // The caller's copy says "common account"; the stored one has since become the
        // default. Resolving at execution is what makes the guard read the second.
        val dao = RecordingAccountDao()
        val useCase = ArchiveAccountUseCaseImpl(
            accountRepository = RecordingAccountRepository(account.copy(isDefault = true)),
            accountDao = dao,
            entryRepository = FakeEntries(hasEntries = false, balance = 0.0),
        )

        val error = assertIs<AccountException>(useCase(account).leftOrNull())

        assertEquals(AccountError.CANNOT_ARCHIVE_DEFAULT, error.error)
        assertTrue(dao.closed.isEmpty(), "the default account must stay open")
    }
}
