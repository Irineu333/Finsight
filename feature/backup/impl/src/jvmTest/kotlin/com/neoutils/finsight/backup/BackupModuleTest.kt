@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup

import com.neoutils.finsight.backup.service.JvmBackupDestination
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.getDatabaseBuilder
import com.neoutils.finsight.database.getRoomDatabase
import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.database.snapshot.CandidateVerifier
import com.neoutils.finsight.di.backupModule
import com.neoutils.finsight.domain.ledger.TransactionRemovalPrelude
import com.neoutils.finsight.domain.model.CURRENCY_SEED
import com.neoutils.finsight.domain.model.CurrencySeeding
import com.neoutils.finsight.domain.model.SeedCurrency
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * The bindings that nothing else would notice were missing.
 *
 * The ledger's removal prelude is optional by design — a graph with nobody claiming it is a
 * valid graph, and the ledger removes correctly either way. That makes it the one binding
 * whose absence compiles, passes every test of the ledger, and only shows up as a user
 * losing a transaction with nothing kept back. So it is asserted through the container,
 * over the real module, rather than trusted.
 *
 * The destination is the one thing swapped out: the desktop one writes beside the archive,
 * in the home directory of whoever runs this.
 */
class BackupModuleTest {

    private val temporaries = mutableListOf<File>()

    private val folder: File = Files.createTempDirectory("finsight-module-vault").toFile()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-module-$name", ".db")
            .also { it.delete(); temporaries += it }

    private fun roomAt(path: String): AppDatabase = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = moduleSeeding(),
    )

    private val live = roomAt(temporary("live").absolutePath)

    private val destination = JvmBackupDestination(
        ownCopy = OwnCopyCheck(CandidateVerifier(::roomAt)),
        directory = folder,
    )

    private val koin = koinApplication {
        modules(
            backupModule,
            module {
                single<AppDatabase> { live }
                single { CandidateVerifier(::roomAt) }
                single<Settings> { MapSettings() }
                single<Clock> { Clock.System }
                single { ModalManager() }
                single<BackupDestination> { destination }
            },
        )
    }.koin

    @AfterTest
    fun tearDown() {
        koin.close()
        live.close()
        (temporaries + folder.listFiles().orEmpty()).forEach { file ->
            listOf("", "-wal", "-shm").forEach { File(file.absolutePath + it).delete() }
        }
        folder.delete()
    }

    /**
     * Resolving it is half the claim; the other half is that it is not the do-nothing the
     * ledger falls back to, which resolves just as happily and protects nothing.
     */
    @Test
    fun `the ledger's removal prelude is claimed`() {
        val prelude = assertNotNull(
            koin.get<TransactionRemovalPrelude>(),
            "nobody claims the port, so every removal happens with nothing kept back",
        )

        assertNotSame(TransactionRemovalPrelude.None, prelude)
    }

    /**
     * The whole road, through the container: the ledger announces a removal, the
     * classification says a transaction is worth a copy, and the vault writes one.
     */
    @Test
    fun `announcing a removal takes the copy that holds what is about to go`() = runTest {
        koin.get<BackupVaultRepository>().setOn(true)
        val entered = live.transactionDao()
            .insert(TransactionEntity(title = "rent", date = DATE))

        koin.get<TransactionRemovalPrelude>().beforeRemoval()

        val copy = assertNotNull(
            assertNotNull(destination.list().getOrNull()).singleOrNull(),
            "the removal was announced and nothing was written",
        )
        val held = roomAt(File(folder, copy.name).also { temporaries += it }.absolutePath)
        try {
            assertNotNull(
                held.transactionDao().getById(entered),
                "the copy does not hold the transaction that was about to go",
            )
        } finally {
            held.close()
        }
    }

    /** The switch governs this road as it governs every other (design D1). */
    @Test
    fun `a vault that is off lets the removal through without writing anything`() = runTest {
        live.transactionDao().insert(TransactionEntity(title = "coffee", date = DATE))

        koin.get<TransactionRemovalPrelude>().beforeRemoval()

        assertEquals(emptyList(), destination.list().getOrNull())
    }

    private companion object {
        val DATE = LocalDate(2026, 8, 30)
    }
}

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun moduleSeeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
