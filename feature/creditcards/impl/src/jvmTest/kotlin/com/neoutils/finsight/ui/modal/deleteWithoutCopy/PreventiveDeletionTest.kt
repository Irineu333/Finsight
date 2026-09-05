@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.deleteWithoutCopy

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.dao.InstallmentDao
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.CreditCardEntity
import com.neoutils.finsight.database.entity.DimensionEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.database.entity.InstallmentEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.mapper.TransactionMapper
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
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.DimensionKind
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.DeleteFutureInvoiceUseCase
import com.neoutils.finsight.domain.usecase.DeleteFutureInvoiceUseCaseImpl
import com.neoutils.finsight.domain.usecase.DeleteInstallmentUseCaseImpl
import com.neoutils.finsight.feature.backup.api.PreventiveCaptureException
import com.neoutils.finsight.feature.backup.api.DestructiveAction
import com.neoutils.finsight.feature.backup.api.PreventiveCoverage
import com.neoutils.finsight.feature.backup.api.VaultOffer
import com.neoutils.finsight.feature.backup.api.VaultOfferTerms
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.modal.deleteFutureInvoice.DeleteFutureInvoiceViewModel
import com.neoutils.finsight.ui.modal.deleteInstallment.DeleteInstallmentViewModel
import com.neoutils.finsight.ui.screen.invoiceTransactions.FakeCategoryRepository
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * Deleting an installment and deleting an invoice — the two removals that take N
 * transactions with them — and the copy owed before either.
 *
 * The copies are asserted by opening the captured file and counting the rows it holds,
 * because "a capture was requested" says nothing about what it saved; and every answer to
 * the question that follows a refused capture is asserted against the `transactions` table,
 * because a screen that swallowed the refusal and deleted anyway would satisfy "it was
 * caught" while destroying exactly what the copy existed for.
 *
 * The invoice half also pins the failure that was not a missing prompt but a crash: the
 * refusal escapes the use case's `Either` — a missing copy is not one of the invoice's own
 * refusals — and nothing used to catch it.
 */
class PreventiveDeletionTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    private val folder: File = Files.createTempDirectory("finsight-preventive-card").toFile()

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
     * asked for, and that a box left ticked is acted on **before** the deletion — a vault turned on afterwards protects nothing.
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
     * Reads the offer's answer at the one moment it has to be true already: a copy is being
     * asked for, and the twelve are still there.
     */
    private class WatchingRemoval(private val vault: OfferingVault) : TransactionRemovalPrelude {

        var acceptedWhenAsked: Boolean? = null
            private set

        override suspend fun beforeRemoval() {
            acceptedWhenAsked = vault.accepted
        }
    }

    /**
     * A vault that covers one action and no other, and remembers which it was asked about.
     *
     * It is what pins the half a screen owns: each sheet asks about the deletion it is a
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

    private object SilentAnalytics : Analytics {
        override fun logScreenView(screenName: String) = Unit
        override fun logEvent(event: Event) = Unit
        override fun setUserId(id: String?) = Unit
    }

    private object SilentCrashlytics : Crashlytics {
        override fun setUserId(id: String?) = Unit
        override fun recordException(e: Throwable) = Unit
    }

    private class RecordingInstallments : IInstallmentRepository {
        val deleted = mutableListOf<Long>()
        override suspend fun getAllInstallments(): List<Installment> = emptyList()
        override fun observeAllInstallments(): Flow<List<Installment>> = flowOf(emptyList())
        // Resolved, because the operation resolves the identity it was given before it
        // removes anything: a store that answered nothing would refuse every deletion
        // here as `NotFound`, and the test would be asserting the refusal.
        override suspend fun getInstallmentById(id: Long): Installment? =
            INSTALLMENT.takeIf { it.id == id }
        override suspend fun createInstallment(count: Int, totalAmount: Double): Long =
            throw NotImplementedError()

        override suspend fun updateInstallment(id: Long, count: Int, totalAmount: Double) =
            throw NotImplementedError()

        override suspend fun deleteInstallmentById(id: Long) {
            deleted += id
        }
    }

    private class SingleInvoice(private val invoice: Invoice) : IInvoiceRepository {
        val deleted = mutableListOf<Long>()
        override suspend fun getInvoiceById(id: Long): Invoice? = invoice.takeIf { it.id == id }
        override suspend fun deleteById(id: Long) {
            deleted += id
        }

        override suspend fun getAllInvoices(): List<Invoice> = listOf(invoice)
        override fun observeAllInvoices(): Flow<List<Invoice>> = flowOf(listOf(invoice))
        override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> =
            throw NotImplementedError()

        override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> = throw NotImplementedError()
        override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
        override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> =
            throw NotImplementedError()

        override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
        override fun observeUnpaidInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
        override fun observeInvoicesToSettle(month: YearMonth): Flow<List<Invoice>> =
            throw NotImplementedError()

        override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> =
            throw NotImplementedError()

        override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> =
            throw NotImplementedError()

        override suspend fun getUnpaidInvoicesByCreditCards(creditCardIds: Collection<Long>): Map<Long, List<Invoice>> =
            throw NotImplementedError()

        override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = throw NotImplementedError()
        override suspend fun insert(invoice: Invoice): Invoice = throw NotImplementedError()
        override suspend fun update(invoice: Invoice) = throw NotImplementedError()
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

    private val card = CreditCard(
        id = 1,
        name = "Card",
        limit = 1000.0,
        closingDay = 10,
        dueDay = 20,
        accountId = 2,
    )

    private val futureInvoice = Invoice(
        id = 1,
        creditCard = card,
        dimensionId = INVOICE_DIMENSION,
        openingMonth = YearMonth(2026, 8),
        closingMonth = YearMonth(2026, 9),
        dueMonth = YearMonth(2026, 9),
        status = Invoice.Status.FUTURE,
    )

    private var seeded = false

    private suspend fun seed() {
        if (seeded) return
        seeded = true
        db.accountDao().insert(
            AccountEntity(id = 1, name = "Checking", type = AccountEntity.Type.ASSET, currency = "BRL"),
        )
        db.accountDao().insert(
            AccountEntity(id = 2, name = "Card", type = AccountEntity.Type.LIABILITY, currency = "BRL"),
        )
        db.accountDao().insert(
            AccountEntity(id = 10, name = "Despesas", type = AccountEntity.Type.EXPENSE, currency = "BRL"),
        )
        db.creditCardDao().insert(
            CreditCardEntity(id = 1, name = "Card", limit = 1000.0, closingDay = 10, dueDay = 20, accountId = 2),
        )
        db.dimensionDao().insert(DimensionEntity(id = INVOICE_DIMENSION, kind = DimensionKind.INVOICE))
    }

    /**
     * Twelve card purchases, each one a real transaction with the invoice's dimension on
     * its liability leg — which is both what an installment is made of and what an invoice
     * carries, so the same rows serve either deletion.
     */
    private suspend fun enterTwelve(installmentId: Long? = null): List<Transaction> {
        seed()
        repeat(12) { index ->
            val transactionId = db.transactionDao().insert(
                TransactionEntity(
                    title = "Instalment ${index + 1}",
                    date = DATE,
                    installmentId = installmentId,
                    installmentNumber = installmentId?.let { index + 1 },
                ),
            )
            db.entryDao().insertAll(
                listOf(
                    EntryEntity(
                        transactionId = transactionId,
                        accountId = 2,
                        amount = -5000,
                        currency = "BRL",
                        dimensionId = INVOICE_DIMENSION,
                    ),
                    EntryEntity(
                        transactionId = transactionId,
                        accountId = 10,
                        amount = 5000,
                        currency = "BRL",
                    ),
                ),
            )
        }
        return repository(TransactionRemovalPrelude.None).getAllTransactions()
    }

    private suspend fun living(): Int = repository(TransactionRemovalPrelude.None)
        .getAllTransactions()
        .size

    /** How many transactions the captured file holds. */
    private fun File.transactionCount(): Long {
        val connection = BundledSQLiteDriver().open(absolutePath)
        return try {
            val statement = connection.prepare("SELECT COUNT(*) FROM transactions")
            try {
                statement.step()
                statement.getLong(0)
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    }

    private fun installmentViewModel(
        transactions: List<Transaction>,
        prelude: TransactionRemovalPrelude,
        installments: RecordingInstallments = RecordingInstallments(),
        vaultOffer: VaultOffer = VaultOffer.None,
        coverage: PreventiveCoverage = PreventiveCoverage.None,
    ) = DeleteInstallmentViewModel(
        installment = INSTALLMENT,
        transactions = transactions,
        categoryRepository = FakeCategoryRepository(),
        deleteInstallmentUseCase = DeleteInstallmentUseCaseImpl(
            repository(prelude),
            installments,
            NamedTransactions(transactions.map { it.id }),
        ),
        modalManager = ModalManager(),
        analytics = SilentAnalytics,
        crashlytics = SilentCrashlytics,
        vaultOffer = vaultOffer,
        coverage = coverage,
    )

    private fun invoiceViewModel(
        prelude: TransactionRemovalPrelude,
        invoices: SingleInvoice = SingleInvoice(futureInvoice),
        vaultOffer: VaultOffer = VaultOffer.None,
        coverage: PreventiveCoverage = PreventiveCoverage.None,
    ) = DeleteFutureInvoiceViewModel(
        invoice = futureInvoice,
        deleteFutureInvoiceUseCase = DeleteFutureInvoiceUseCaseImpl(invoices, repository(prelude)),
        modalManager = ModalManager(),
        analytics = SilentAnalytics,
        crashlytics = SilentCrashlytics,
        vaultOffer = vaultOffer,
        coverage = coverage,
    )

    // ------------------------------------------------------------------ the installment

    @Test
    fun `the copy taken before an installment goes holds all twelve of it`() = runTest {
        val transactions = enterTwelve(installmentId = INSTALLMENT.id)
        val vault = Capturing()

        DeleteInstallmentUseCaseImpl(
            repository(vault),
            RecordingInstallments(),
            NamedTransactions(transactions.map { it.id }),
        )(INSTALLMENT)

        val copy = vault.files.singleOrNull()
        assertNotNull(copy, "no copy was taken before the twelve went")
        assertEquals(12L, copy.transactionCount(), "the copy does not hold what was removed")
        assertEquals(0, living(), "the deletion itself must still have happened")
    }

    @Test
    fun `a refused copy stops the installment deletion and asks, with all twelve still there`() =
        runTest {
            val transactions = enterTwelve(installmentId = INSTALLMENT.id)

            val viewModel = installmentViewModel(transactions, Refusing())
            val job = viewModel.deleteInstallment()

            assertNotNull(
                viewModel.captureRefusal.first { it != null },
                "the question says why there is no copy",
            )
            assertEquals(12, living(), "nothing may be destroyed before the answer")

            // Unparked so the deletion's coroutine ends with the test rather than outliving it.
            viewModel.abandonDeletion()
            job.join()
        }

    @Test
    fun `saying yes deletes the twelve, and takes no copy on the way`() = runTest {
        val transactions = enterTwelve(installmentId = INSTALLMENT.id)
        val vault = Refusing()
        val installments = RecordingInstallments()

        val viewModel = installmentViewModel(transactions, vault, installments)
        val job = viewModel.deleteInstallment()
        viewModel.captureRefusal.first { it != null }

        viewModel.deleteWithoutCopy()
        job.join()

        assertEquals(0, living(), "yes means the twelve go")
        assertEquals(listOf(INSTALLMENT.id), installments.deleted, "and the installment with them")
        assertNull(viewModel.captureRefusal.value, "there is nothing left to ask")
        assertEquals(1, vault.asked, "the second attempt went without a copy, not for another one")
    }

    @Test
    fun `saying no leaves the twelve exactly as they were`() = runTest {
        val transactions = enterTwelve(installmentId = INSTALLMENT.id)
        val vault = Refusing()
        val installments = RecordingInstallments()

        val viewModel = installmentViewModel(transactions, vault, installments)
        val job = viewModel.deleteInstallment()
        viewModel.captureRefusal.first { it != null }

        viewModel.abandonDeletion()
        job.join()

        assertNull(viewModel.captureRefusal.value)
        assertEquals(12, living())
        assertTrue(installments.deleted.isEmpty(), "and the installment stays too")
        assertEquals(1, vault.asked)
    }

    // ------------------------------------------------- the vault, offered in place

    /**
     * The vault is offered where the risk it covers is — the largest thing this app
     * destroys on one confirmation — and accepting it is acted on before anything goes,
     * because a vault turned on after the deletion has nothing left to copy.
     */
    @Test
    fun `taking the offer beside the deletion turns the vault on before the twelve go`() =
        runTest {
            val transactions = enterTwelve(installmentId = INSTALLMENT.id)
            val vault = OfferingVault()
            val removal = WatchingRemoval(vault)

            val viewModel = installmentViewModel(
                transactions = transactions,
                prelude = removal,
                vaultOffer = vault,
            )

            assertNotNull(viewModel.offer.terms, "a vault that is off is offered beside the risk")
            assertTrue(
                viewModel.offer.isAccepted.value,
                "the offer is made, not merely displayed",
            )

            viewModel.deleteInstallment().join()

            assertTrue(vault.accepted, "one yes, and the whole vault is on")
            assertEquals(
                true,
                removal.acceptedWhenAsked,
                "a vault turned on after the deletion has nothing left to copy",
            )
            assertEquals(0, living(), "and the deletion still happened")
        }

    @Test
    fun `clearing the box deletes the twelve and leaves the vault alone`() = runTest {
        val transactions = enterTwelve(installmentId = INSTALLMENT.id)
        val vault = OfferingVault()

        val viewModel = installmentViewModel(
            transactions = transactions,
            prelude = TransactionRemovalPrelude.None,
            vaultOffer = vault,
        )
        viewModel.offer.setAccepted(false)

        viewModel.deleteInstallment().join()

        assertFalse(vault.accepted, "the box was cleared")
        assertTrue(vault.declined, "and going ahead with it cleared is the answer")
        assertEquals(0, living(), "the deletion is the user's answer either way")
    }

    /**
     * Somebody who said no is not asked again as though nothing had happened — but they are
     * still asked. The screen shows what it is handed, and after a refusal what it is handed
     * is the same offer with the box empty.
     */
    @Test
    fun `a deletion after a refusal still offers, unticked`() = runTest {
        val transactions = enterTwelve(installmentId = INSTALLMENT.id)
        val vault = OfferingVault()

        val first = installmentViewModel(
            transactions = transactions,
            prelude = TransactionRemovalPrelude.None,
            vaultOffer = vault,
        )
        first.offer.setAccepted(false)
        first.deleteInstallment().join()

        val second = installmentViewModel(
            transactions = transactions,
            prelude = TransactionRemovalPrelude.None,
            vaultOffer = vault,
        )

        val terms = assertNotNull(second.offer.terms, "the offer stands while the vault is off")

        assertFalse(second.offer.isAccepted.value, "and it arrives with the answer given last")
        assertTrue(terms.wasDeclined, "worded as the reminder it is")
    }

    /**
     * The offer rides on **whichever** destructive confirmation comes first, and one yes
     * ends it everywhere. An installment sheet whose offer was accepted leaves an invoice
     * sheet with nothing to show, and the reverse is true too — neither feature owns the
     * question.
     */
    @Test
    fun `an offer accepted beside the installment leaves the invoice sheet nothing to show`() =
        runTest {
            val transactions = enterTwelve(installmentId = INSTALLMENT.id)
            val vault = OfferingVault()

            val first = installmentViewModel(
                transactions = transactions,
                prelude = TransactionRemovalPrelude.None,
                vaultOffer = vault,
            )
            assertNotNull(first.offer.terms, "the confirmation reached first carries it")
            first.deleteInstallment().join()

            val second = invoiceViewModel(TransactionRemovalPrelude.None, vaultOffer = vault)

            assertNull(second.offer.terms, "the vault is on; the next one carries nothing")
        }

    /**
     * The sheet asks about the deletion it confirms and says what it is told. It carries no
     * list of its own: handed a vault that covers something else, it stops promising a copy
     * without a line of it changing (design D7).
     */
    @Test
    fun `the installment sheet says what it is told about its own deletion`() = runTest {
        val transactions = enterTwelve(installmentId = INSTALLMENT.id)

        val covered = RecordingCoverage(DestructiveAction.DELETE_INSTALLMENT)
        val keeping = installmentViewModel(
            transactions = transactions,
            prelude = TransactionRemovalPrelude.None,
            coverage = covered,
        )

        assertTrue(keeping.keepsCopy.value, "a copy is kept and the sheet was told otherwise")
        assertEquals(DestructiveAction.DELETE_INSTALLMENT, covered.asked)

        val plain = installmentViewModel(
            transactions = transactions,
            prelude = TransactionRemovalPrelude.None,
            coverage = RecordingCoverage(DestructiveAction.DELETE_CURRENCY),
        )

        assertFalse(plain.keepsCopy.value, "no copy is kept and the sheet still promised one")
    }

    // ---------------------------------------------------------------------- the invoice

    @Test
    fun `the copy taken before an invoice goes holds the transactions it carried`() = runTest {
        enterTwelve()
        val vault = Capturing()
        val invoices = SingleInvoice(futureInvoice)

        DeleteFutureInvoiceUseCaseImpl(invoices, repository(vault))(futureInvoice.id)

        val copy = vault.files.singleOrNull()
        assertNotNull(copy, "no copy was taken before the invoice went")
        assertEquals(12L, copy.transactionCount(), "the copy does not hold what was removed")
        assertEquals(0, living(), "the deletion itself must still have happened")
        assertEquals(listOf(futureInvoice.id), invoices.deleted)
    }

    /**
     * The refusal escapes the use case's `Either`, and this is where it stops being a
     * crash: uncaught it took the app down with the invoice still there, which is the one
     * outcome that looked like protection and was not.
     */
    @Test
    fun `a refused copy stops the invoice deletion and asks, with everything still there`() =
        runTest {
            enterTwelve()
            val invoices = SingleInvoice(futureInvoice)

            val viewModel = invoiceViewModel(Refusing(), invoices)
            val job = viewModel.deleteInvoice()

            assertNotNull(
                viewModel.captureRefusal.first { it != null },
                "the question says why there is no copy",
            )
            assertEquals(12, living(), "nothing may be destroyed before the answer")
            assertTrue(invoices.deleted.isEmpty(), "and the invoice is still there")

            // Unparked so the deletion's coroutine ends with the test rather than outliving it.
            viewModel.abandonDeletion()
            job.join()
        }

    @Test
    fun `saying yes deletes the invoice, and takes no copy on the way`() = runTest {
        enterTwelve()
        val vault = Refusing()
        val invoices = SingleInvoice(futureInvoice)

        val viewModel = invoiceViewModel(vault, invoices)
        val job = viewModel.deleteInvoice()
        viewModel.captureRefusal.first { it != null }

        viewModel.deleteWithoutCopy()
        job.join()

        assertEquals(0, living(), "yes means the transactions go")
        assertEquals(listOf(futureInvoice.id), invoices.deleted)
        assertNull(viewModel.captureRefusal.value)
        assertEquals(1, vault.asked, "the second attempt went without a copy, not for another one")
    }

    @Test
    fun `saying no leaves the invoice and its transactions where they are`() = runTest {
        enterTwelve()
        val vault = Refusing()
        val invoices = SingleInvoice(futureInvoice)

        val viewModel = invoiceViewModel(vault, invoices)
        val job = viewModel.deleteInvoice()
        viewModel.captureRefusal.first { it != null }

        viewModel.abandonDeletion()
        job.join()

        assertNull(viewModel.captureRefusal.value)
        assertEquals(12, living())
        assertTrue(invoices.deleted.isEmpty())
        assertEquals(1, vault.asked)
    }

    /**
     * An invoice carrying nothing is still an invoice the sheet promised a copy before.
     *
     * The promise is `DestructiveAction.DELETE_INVOICE`'s class and nothing else reads the
     * row count (`DeleteFutureInvoiceViewModel.keepsCopy`), so a confirmation over an empty
     * invoice shows the kept-copy notice exactly as one over twelve rows does. Said per
     * row, the announcement rode on the *first* row and an invoice with none said it never:
     * the notice stood, no copy was taken, and the invoice went anyway — protection claimed
     * and not given, which is the one thing the screen may not do.
     *
     * The sibling deletion never had this: `deleteTransactionsByIds` announces before it
     * looks at the list, so an empty batch still speaks, and that is now the call both
     * take.
     */
    @Test
    fun `an invoice carrying nothing still takes the copy its sheet promised`() = runTest {
        seed()
        val vault = Capturing()
        val invoices = SingleInvoice(futureInvoice)

        val outcome = DeleteFutureInvoiceUseCaseImpl(invoices, repository(vault))(futureInvoice.id)

        assertTrue(outcome.isRight(), "an empty future invoice is deletable")
        assertEquals(
            1,
            vault.files.size,
            "the sheet promised a copy before an empty invoice and none was taken",
        )
        assertEquals(listOf(futureInvoice.id), invoices.deleted)
    }

    /**
     * A deletion the domain refuses destroys nothing, so there is nothing to keep a copy
     * of — and the refusal is reached before the removal is ever announced.
     */
    @Test
    fun `an invoice the domain refuses to delete asks for no copy`() = runTest {
        enterTwelve()
        val vault = Refusing()
        val paid = futureInvoice.copy(status = Invoice.Status.PAID)
        val invoices = SingleInvoice(paid)

        val outcome = DeleteFutureInvoiceUseCaseImpl(invoices, repository(vault))(paid.id)

        assertTrue(outcome.isLeft(), "a paid invoice is not deletable")
        assertEquals(0, vault.asked, "a refused deletion must not reach for a copy")
        assertEquals(12, living())
        assertTrue(invoices.deleted.isEmpty())
    }

    /**
     * An invoice takes every transaction posted to it, and it is one of the five that carry
     * the offer. Accepting is acted on **before** they go.
     */
    @Test
    fun `taking the offer beside the invoice turns the vault on before its rows go`() =
        runTest {
            enterTwelve()
            val vault = OfferingVault()
            val removal = WatchingRemoval(vault)

            val viewModel = invoiceViewModel(removal, vaultOffer = vault)

            assertNotNull(viewModel.offer.terms, "a vault that is off is offered beside the risk")
            assertTrue(viewModel.offer.isAccepted.value, "the offer is made, not merely displayed")

            viewModel.deleteInvoice().join()

            assertTrue(vault.accepted, "one yes, and the whole vault is on")
            assertEquals(
                true,
                removal.acceptedWhenAsked,
                "a vault turned on after the deletion has nothing left to copy",
            )
        }

    @Test
    fun `the invoice sheet says what it is told about its own deletion`() = runTest {
        val covered = RecordingCoverage(DestructiveAction.DELETE_INVOICE)
        val keeping = invoiceViewModel(TransactionRemovalPrelude.None, coverage = covered)

        assertTrue(keeping.keepsCopy.value, "a copy is kept and the sheet was told otherwise")
        assertEquals(DestructiveAction.DELETE_INVOICE, covered.asked)

        val plain = invoiceViewModel(
            prelude = TransactionRemovalPrelude.None,
            coverage = RecordingCoverage(DestructiveAction.DELETE_INSTALLMENT),
        )

        assertFalse(plain.keepsCopy.value, "no copy is kept and the sheet still promised one")
    }

    private companion object {
        val DATE = LocalDate(2026, 8, 30)
        const val INVOICE_DIMENSION = 1L
        val INSTALLMENT = Installment(id = 7, count = 12, totalAmount = 600.0)
    }
}

/**
 * The installment's own question about the transactions that name it, answered from the
 * list the test entered — the one read `DeleteInstallmentUseCase` makes of this DAO.
 */
private class NamedTransactions(private val ids: List<Long>) : InstallmentDao {
    override suspend fun transactionIds(installmentId: Long): List<Long> = ids
    override suspend fun countTransactions(installmentId: Long): Int = ids.size
    override suspend fun detachTransactions(installmentId: Long) = throw NotImplementedError()
    override suspend fun insert(installment: InstallmentEntity): Long = throw NotImplementedError()
    override suspend fun getById(id: Long): InstallmentEntity? = throw NotImplementedError()
    override fun observeAll(): Flow<List<InstallmentEntity>> = throw NotImplementedError()
    override suspend fun getAll(): List<InstallmentEntity> = throw NotImplementedError()
    override suspend fun deleteById(id: Long) = throw NotImplementedError()
    override suspend fun updateById(id: Long, count: Int, totalAmount: Double) = throw NotImplementedError()
}
