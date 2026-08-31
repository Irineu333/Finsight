@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.deleteTransaction

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.mapper.TransactionMapper
import com.neoutils.finsight.database.repository.FakeCategoryRepository
import com.neoutils.finsight.database.repository.LedgerEntryWriter
import com.neoutils.finsight.database.repository.TransactionRepository
import com.neoutils.finsight.database.snapshot.captureInto
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.ledger.DimensionWriteGuard
import com.neoutils.finsight.domain.ledger.TransactionRemovalHook
import com.neoutils.finsight.domain.ledger.TransactionRemovalPrelude
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.usecase.DeleteTransactionUseCaseImpl
import com.neoutils.finsight.feature.backup.api.DestructiveAction
import com.neoutils.finsight.feature.backup.api.PreventiveCaptureException
import com.neoutils.finsight.feature.backup.api.ProceedWithoutCopyModal
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.delete_transaction_confirm
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.UiText
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import com.neoutils.finsight.feature.backup.api.PreventiveCoverage
import com.neoutils.finsight.feature.backup.api.VaultOffer
import com.neoutils.finsight.feature.backup.api.VaultOfferTerms

/**
 * Deleting a transaction, and the copy that is owed before it.
 *
 * Two things are pinned here, and neither is that an exception was caught. The first is
 * that the file taken beforehand **holds the transaction that is about to go** — asserted
 * by opening the captured file and reading the row out of it, because a capture that was
 * merely requested proves nothing about what it saved. The second is what happens when no
 * file could be written: the removal stops, the question goes up, and each of the three
 * answers — yes, no, and walking away — is checked against the `transactions` table rather
 * than against the sheet, since a screen that swallowed the refusal and deleted anyway
 * would satisfy "it was caught" and destroy the very thing the copy existed for.
 */
class PreventiveDeletionTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    private val folder: File = Files.createTempDirectory("finsight-preventive-transaction").toFile()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
        folder.listFiles().orEmpty().forEach { it.delete() }
        folder.delete()
    }

    // ------------------------------------------------------------------- the fixture

    /** A vault that writes a real file, and says which one it wrote. */
    private inner class Capturing : TransactionRemovalPrelude {

        val files = mutableListOf<File>()

        override suspend fun beforeRemoval() {
            val file = File(folder, "copy-${files.size}.db")
            db.captureInto(
                destinationPath = file.absolutePath,
                appVersion = "1.2.3",
                platform = "desktop",
            )
            files += file
        }
    }

    /**
     * A vault with nowhere to write, counting the times it was asked.
     *
     * The count is what says the second attempt really went **without** a copy: a screen
     * that answered the question by simply retrying would ask twice, be refused twice, and
     * never destroy anything.
     */
    private class Refusing : TransactionRemovalPrelude {

        var asked = 0
            private set

        override suspend fun beforeRemoval() {
            asked++
            throw PreventiveCaptureException(
                reason = UiText.Raw("nowhere to write"),
                message = "nowhere to write",
            )
        }
    }

    /**
     * A vault that is off, can be turned on, and remembers both answers it was given.
     *
     * What turning the vault on actually does is `StandingVaultOffer`'s and is pinned in the
     * backup module. What is asserted here is the half this screen owns: that the offer is
     * asked for, and that a box left ticked is acted on **before** the removal.
     */
    private class OfferingVault : VaultOffer {

        var accepted = false
            private set

        var declined = false
            private set

        // Accepting is what turns the vault on, and a vault that is on has nothing left to
        // offer — which is the only thing that stops the offer being made.
        override fun offer(): VaultOfferTerms? {
            if (accepted) return null

            return VaultOfferTerms(
                intervalLabel = UiText.Raw("3 days"),
                wasDeclined = declined,
                decline = { declined = true },
            ) { accepted = true }
        }
    }

    /**
     * A vault that covers one action and no other, and remembers which it was asked about.
     *
     * It is what pins the half the screen owns: this sheet asks about the deletion it is a
     * confirmation of, and shows what it is told. Which actions are worth a copy is decided
     * behind this and asserted in the backup module.
     */
    private class RecordingCoverage(private val covered: DestructiveAction) : PreventiveCoverage {

        var asked: DestructiveAction? = null
            private set

        override fun keepsCopyBefore(action: DestructiveAction): Boolean {
            asked = action
            return action == covered
        }
    }

    /** Reads the offer's answer at the one moment it has to be true already. */
    private class WatchingRemoval(private val vault: OfferingVault) : TransactionRemovalPrelude {

        var acceptedWhenAsked: Boolean? = null
            private set

        override suspend fun beforeRemoval() {
            acceptedWhenAsked = vault.accepted
        }
    }

    private object SilentAnalytics : Analytics {
        override fun logScreenView(screenName: String) = Unit
        override fun logEvent(event: Event) = Unit
        override fun setUserId(id: String?) = Unit
    }

    private object SilentCrashlytics : Crashlytics {
        override fun setUserId(id: String?) = Unit
        override fun recordException(e: Throwable) = Unit
    }

    private fun repository(prelude: TransactionRemovalPrelude) = TransactionRepository(
        database = db,
        transactionDao = db.transactionDao(),
        entryDao = db.entryDao(),
        accountDao = db.accountDao(),
        writeGuard = DimensionWriteGuard.None,
        removalHook = TransactionRemovalHook.None,
        removalPrelude = prelude,
        transactionMapper = TransactionMapper(),
        ledgerEntryWriter = LedgerEntryWriter(db.entryDao(), db.accountDao(), db.dimensionDao()),
    )

    private fun viewModel(
        transaction: Transaction,
        prelude: TransactionRemovalPrelude,
        vaultOffer: VaultOffer = VaultOffer.None,
        coverage: PreventiveCoverage = PreventiveCoverage.None,
    ) = DeleteTransactionViewModel(
        transaction = transaction,
        categoryRepository = FakeCategoryRepository,
        deleteTransactionUseCase = DeleteTransactionUseCaseImpl(repository(prelude)),
        modalManager = ModalManager(),
        analytics = SilentAnalytics,
        crashlytics = SilentCrashlytics,
        vaultOffer = vaultOffer,
        coverage = coverage,
    )

    private var seeded = false

    /** What the user entering an expense looks like from here. */
    private suspend fun enter(title: String): Transaction {
        if (!seeded) {
            seeded = true
            db.accountDao().insert(
                AccountEntity(id = 1, name = "Checking", type = AccountEntity.Type.ASSET, currency = "BRL"),
            )
            db.accountDao().insert(
                AccountEntity(id = 10, name = "Despesas", type = AccountEntity.Type.EXPENSE, currency = "BRL"),
            )
        }

        return repository(TransactionRemovalPrelude.None).createTransaction(
            TransactionIntent(
                title = title,
                date = DATE,
                legs = listOf(
                    TransactionLeg(type = TransactionType.EXPENSE, amount = 50.0, accountId = 1),
                ),
                contra = ContraLeg(AccountType.EXPENSE),
            ),
        )
    }

    /** Whether the captured file holds the row this deletion was about to remove. */
    private fun File.holds(id: Long): Boolean {
        val connection = BundledSQLiteDriver().open(absolutePath)
        return try {
            val statement = connection.prepare("SELECT COUNT(*) FROM transactions WHERE id = ?1")
            try {
                statement.bindLong(1, id)
                statement.step()
                statement.getLong(0) == 1L
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    }

    // ----------------------------------------------------- the copy taken beforehand

    @Test
    fun `the copy taken before the deletion holds the transaction it removes`() = runTest {
        val entered = enter("rent")
        val vault = Capturing()

        DeleteTransactionUseCaseImpl(repository(vault))(entered)

        val copy = vault.files.singleOrNull()
        assertNotNull(copy, "no copy was taken before the deletion")
        assertTrue(copy.holds(entered.id), "the copy does not hold what the deletion removed")
        assertNull(
            db.transactionDao().getById(entered.id),
            "the deletion itself must still have happened",
        )
    }

    // --------------------------------------------------------- the copy that refuses

    @Test
    fun `a refused copy stops the deletion and asks, with the transaction still there`() =
        runTest {
            val entered = enter("rent")

            val viewModel = viewModel(entered, Refusing())
            val job = viewModel.deleteTransaction()

            assertNotNull(
                viewModel.captureRefusal.first { it != null },
                "the question says why there is no copy",
            )
            assertNotNull(
                db.transactionDao().getById(entered.id),
                "nothing may be destroyed before the answer",
            )

            // Unparked so the deletion's coroutine ends with the test rather than outliving it.
            viewModel.abandonDeletion()
            job.join()
        }

    @Test
    fun `saying yes deletes the transaction, and takes no copy on the way`() = runTest {
        val entered = enter("rent")
        val vault = Refusing()

        val viewModel = viewModel(entered, vault)
        val job = viewModel.deleteTransaction()
        viewModel.captureRefusal.first { it != null }

        viewModel.deleteWithoutCopy()
        job.join()

        assertNull(db.transactionDao().getById(entered.id), "yes means the transaction goes")
        assertNull(viewModel.captureRefusal.value, "there is nothing left to ask")
        assertEquals(1, vault.asked, "the second attempt went without a copy, not for another one")
    }

    @Test
    fun `saying no leaves the transaction exactly as it was`() = runTest {
        val entered = enter("rent")
        val vault = Refusing()

        val viewModel = viewModel(entered, vault)
        val job = viewModel.deleteTransaction()
        viewModel.captureRefusal.first { it != null }

        viewModel.abandonDeletion()
        job.join()

        assertNull(viewModel.captureRefusal.value)
        assertNotNull(db.transactionDao().getById(entered.id))
        assertEquals(1, vault.asked)
    }

    /**
     * Walking away from the question is answering no. The sheet routes its own dismissal to
     * the same answer the cancel button gives, so a scrim tap cannot become permission.
     */
    @Test
    fun `dismissing the question is answering no`() = runTest {
        val entered = enter("rent")

        val viewModel = viewModel(entered, Refusing())
        val job = viewModel.deleteTransaction()
        val reason = viewModel.captureRefusal.first { it != null }

        ProceedWithoutCopyModal(
            reason = requireNotNull(reason),
            action = Res.string.delete_transaction_confirm,
            onProceed = viewModel::deleteWithoutCopy,
            onAbandon = viewModel::abandonDeletion,
        ).onDismissed()
        job.join()

        assertNull(viewModel.captureRefusal.value)
        assertNotNull(db.transactionDao().getById(entered.id))
    }

    // ------------------------------------------------- the vault, offered in place

    /**
     * The offer rides on the first destructive confirmation the person reaches, and for
     * most people that is this one. Accepting is acted on **before** the transaction goes,
     * because a vault turned on afterwards has nothing left to copy.
     */
    @Test
    fun `taking the offer beside the deletion turns the vault on before the row goes`() =
        runTest {
            val entered = enter("rent")
            val vault = OfferingVault()
            val removal = WatchingRemoval(vault)

            val viewModel = viewModel(entered, removal, vaultOffer = vault)

            assertNotNull(viewModel.offer.terms, "a vault that is off is offered beside the risk")
            assertTrue(viewModel.offer.isAccepted.value, "the offer is made, not merely displayed")

            viewModel.deleteTransaction().join()

            assertTrue(vault.accepted, "one yes, and the whole vault is on")
            assertEquals(
                true,
                removal.acceptedWhenAsked,
                "a vault turned on after the deletion has nothing left to copy",
            )
            assertNull(db.transactionDao().getById(entered.id), "and the deletion happened")
        }

    @Test
    fun `clearing the box deletes the transaction and leaves the vault alone`() = runTest {
        val entered = enter("rent")
        val vault = OfferingVault()

        val viewModel = viewModel(entered, TransactionRemovalPrelude.None, vaultOffer = vault)
        viewModel.offer.setAccepted(false)

        viewModel.deleteTransaction().join()

        assertFalse(vault.accepted, "the box was cleared")
        assertTrue(vault.declined, "and going ahead with it cleared is the answer")
        assertNull(db.transactionDao().getById(entered.id))
    }

    /**
     * Declining does not withdraw the offer, it changes its tone. The switch lives on a
     * screen nobody visits, so a deletion is the only place the vault is met — and what a
     * refusal buys is a box that arrives empty rather than one that stops arriving.
     */
    @Test
    fun `a deletion after a refusal still offers, unticked`() = runTest {
        val entered = enter("rent")
        val vault = OfferingVault()

        val first = viewModel(entered, TransactionRemovalPrelude.None, vaultOffer = vault)
        first.offer.setAccepted(false)
        first.deleteTransaction().join()

        val second = viewModel(enter("water"), TransactionRemovalPrelude.None, vaultOffer = vault)

        val terms = assertNotNull(second.offer.terms, "the offer stands while the vault is off")

        assertFalse(second.offer.isAccepted.value, "and it arrives with the answer given last")
        assertTrue(terms.wasDeclined, "worded as the reminder it is")
    }

    /**
     * The gate is the vault's and not this screen's: what stops the offer is there being
     * nothing left to turn on, and one yes anywhere is enough.
     */
    @Test
    fun `a confirmation on a vault already on shows no offer`() = runTest {
        val entered = enter("rent")
        val vault = OfferingVault()

        viewModel(entered, TransactionRemovalPrelude.None, vaultOffer = vault)
            .deleteTransaction()
            .join()

        val second = viewModel(enter("water"), TransactionRemovalPrelude.None, vaultOffer = vault)

        assertNull(second.offer.terms, "the vault is on; there is nothing to offer")
        assertFalse(second.offer.isAccepted.value, "and there is nothing ticked to act on")
    }

    // ------------------------------------------- what the sheet says about undoing it

    /**
     * The sheet asks about the deletion it confirms and says what it is told. It carries no
     * list of its own: handed a vault that covers something else, it stops promising a copy
     * without a line of it changing (design D7).
     */
    @Test
    fun `the sentence follows the answer given about this deletion`() = runTest {
        val entered = enter("rent")

        val covered = RecordingCoverage(DestructiveAction.DELETE_TRANSACTION)
        val keeping = viewModel(entered, TransactionRemovalPrelude.None, coverage = covered)

        assertTrue(keeping.keepsCopy, "a copy is kept and the sheet was told otherwise")
        assertEquals(
            DestructiveAction.DELETE_TRANSACTION,
            covered.asked,
            "the sheet asked about an action other than its own",
        )

        val elsewhere = RecordingCoverage(DestructiveAction.DELETE_CURRENCY)
        val plain = viewModel(entered, TransactionRemovalPrelude.None, coverage = elsewhere)

        assertFalse(plain.keepsCopy, "no copy is kept and the sheet still promised one")
    }

    private companion object {
        val DATE = LocalDate(2026, 8, 30)
    }
}
