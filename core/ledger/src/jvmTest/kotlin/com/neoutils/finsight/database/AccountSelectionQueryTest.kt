package com.neoutils.finsight.database

import com.neoutils.finsight.database.entity.AccountEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which rows of the chart of accounts reach a screen, and which never do.
 *
 * Two rules ride on these predicates and neither had a test. **Only `ASSET` rows are
 * accounts to the user** — the same table holds the cards, the two nominals and
 * reconciliation, and a leak would put "Despesas" in the account picker of a new
 * transaction. And **an archived account leaves the selectors but keeps its
 * history**, which is the whole point of closing instead of deleting
 * (`account-lifecycle`).
 *
 * The second is why the nominals need no hiding of their own: they are invisible by
 * construction, for the same reason reconciliation always was (design D10).
 */
class AccountSelectionQueryTest {

    private val database = ledgerDatabase()
    private val accountDao = database.accountDao()

    @AfterTest fun tearDown() = database.close()

    private suspend fun seed() {
        val fixture = LedgerFixture(database)
        fixture.account(1, AccountEntity.Type.ASSET, "Checking")
        fixture.account(2, AccountEntity.Type.ASSET, "Savings")
        fixture.account(3, AccountEntity.Type.LIABILITY, "Card")
        fixture.account(4, AccountEntity.Type.EXPENSE, "Despesas")
        fixture.account(5, AccountEntity.Type.INCOME, "Receitas")
        fixture.account(6, AccountEntity.Type.EQUITY, "Reconciliação")
        fixture.account(7, AccountEntity.Type.CONVERSION, "CONVERSION")
        fixture.account(8, AccountEntity.Type.CONVERSION, "CONVERSION", currency = "USD")
    }

    @Test
    fun `only the user's own accounts are offered, never a card or a nominal`() = runTest {
        seed()

        assertEquals(
            listOf("Checking", "Savings"),
            accountDao.getAllAccounts().map { it.name },
            "the card, the two nominals and reconciliation share the table and must not leak",
        )
    }

    @Test
    fun `an archived account is not offered`() = runTest {
        seed()

        accountDao.close(2)

        assertEquals(listOf("Checking"), accountDao.getAllAccounts().map { it.name })
    }

    @Test
    fun `but it is still there for the history that references it`() = runTest {
        seed()

        accountDao.close(2)

        assertEquals(
            listOf("Checking", "Savings"),
            accountDao.getAllAccountsIncludingClosed().map { it.name },
            "closing keeps the row: its entries stay valid and its name still renders",
        )
        assertEquals(8, accountDao.getAllLedgerAccounts().size, "and the chart itself is untouched")
    }

    // --- the conversion accounts are hidden by the same predicate (task 4.9) ---

    @Test
    fun `a conversion account is never offered, in any currency`() = runTest {
        seed()

        // No hiding of its own: `type = 'ASSET'` already excludes it, exactly as it
        // excludes the two nominals and reconciliation. The write boundary creates one
        // per currency on demand and nothing else may post to it, so its name — a
        // lookup key, never a label — has no business reaching a screen.
        assertEquals(listOf("Checking", "Savings"), accountDao.getAllAccounts().map { it.name })
        assertEquals(
            listOf("Checking", "Savings"),
            accountDao.getAllAccountsIncludingClosed().map { it.name },
            "not even among the closed ones, where history is rendered",
        )
        assertEquals(2, accountDao.getAccountCount())
    }

    @Test
    fun `a conversion account is resolved by the triple, one per currency`() = runTest {
        seed()

        assertEquals(
            7L,
            accountDao.getByTypeAndName(AccountEntity.Type.CONVERSION, "CONVERSION", "BRL")?.id,
        )
        assertEquals(
            8L,
            accountDao.getByTypeAndName(AccountEntity.Type.CONVERSION, "CONVERSION", "USD")?.id,
        )
        assertEquals(
            null,
            accountDao.getByTypeAndName(AccountEntity.Type.CONVERSION, "CONVERSION", "EUR"),
            "a currency with no conversion account yet is absent, not somebody else's row",
        )
    }

    @Test
    fun `the default account is never an archived one`() = runTest {
        seed()
        accountDao.update(accountDao.getAccountById(1)!!.copy(isDefault = true))

        assertEquals("Checking", accountDao.getDefaultAccount()?.name)

        accountDao.close(1)

        assertEquals(null, accountDao.getDefaultAccount(), "a closed default is no default")
    }
}
