@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.backup

import arrow.core.Either
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowScope
import arrow.core.right
import com.neoutils.finsight.backup.service.JvmBackupDestination
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.getDatabaseBuilder
import com.neoutils.finsight.database.getRoomDatabase
import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.database.repository.RoomArchiveMark
import com.neoutils.finsight.database.snapshot.CandidateVerification
import com.neoutils.finsight.database.snapshot.CandidateVerifier
import com.neoutils.finsight.database.snapshot.captureInto
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.model.BackupPlatform
import com.neoutils.finsight.domain.model.CURRENCY_SEED
import com.neoutils.finsight.domain.model.CaptureOrigin
import com.neoutils.finsight.domain.model.CurrencySeeding
import com.neoutils.finsight.domain.model.SeedCurrency
import com.neoutils.finsight.domain.vault.ArchiveImport
import com.neoutils.finsight.domain.vault.BackupRetention
import com.neoutils.finsight.domain.vault.BackupVault
import com.neoutils.finsight.domain.vault.CaptureOutcome
import com.neoutils.finsight.domain.vault.ImportOutcome
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.neoutils.finsight.ui.screen.backup.service.isBackupFileName
import com.neoutils.finsight.ui.screen.backup.service.isImportedFileName
import com.russhwolf.settings.MapSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * Bringing a backup file into the destination, over real files.
 *
 * Everything here is real — a real archive, real captures, the real gate and a real folder —
 * because every one of the four claims is about what is on disk afterwards. A fake
 * destination would answer whatever the test wanted about a file landing, which is the only
 * thing worth asserting; a fake verifier would make the refusal a stub agreeing with itself.
 *
 * The four claims, one per group below: a file that is not this app's does not land at all;
 * one that is lands under a name the listing shows and comes back out intact; nothing about
 * which copy the app is standing on moves; and retention counts what arrived like any other
 * copy.
 */
class ArchiveImportTest {

    private val temporaries = mutableListOf<File>()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-import-$name", ".db")
            .also { it.delete(); temporaries += it }

    private fun open(path: String): AppDatabase = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = importSeeding(),
    )

    private val live = open(temporary("live").absolutePath)
    private val verifier = CandidateVerifier(::open)

    private val folder: File = Files.createTempDirectory("finsight-import").toFile()

    private val destination = JvmBackupDestination(
        ownCopy = OwnCopyCheck(verifier),
        directory = folder,
    )

    private val state = BackupVaultRepository(MapSettings())

    /** Moved by hand, so two copies are never asked for at the same second. */
    private var instant: Instant = Instant.parse("2026-08-30T10:00:00Z")

    private val clock = object : Clock {
        override fun now(): Instant = instant
    }

    private var handedOut = 0

    /** What the person picked, waiting to be handed over as a copy this app may lose. */
    private var picked: File? = null

    private val files = object : BackupFileService {

        override suspend fun newCapturePath(): Either<BackupError, String> =
            temporary("out-${handedOut++}").absolutePath.right()

        override suspend fun discard(path: String) {
            DATABASE_FILES.forEach { File(path + it).delete() }
        }

        /**
         * The picker, as the app sees it: a private copy of what the person chose, at a
         * path this app may write to and lose. The original is left alone, which is what
         * makes the verification safe to run on the copy.
         */
        override suspend fun copyInChosenFile(
            context: PlatformContext,
        ): Either<BackupError, String?> {
            val chosen = picked ?: return null.right()
            val into = temporary("picked-${handedOut++}")
            chosen.copyTo(into, overwrite = true)
            return into.absolutePath.right()
        }

        override suspend fun copyOutCapturedFile(
            sourcePath: String,
            suggestedName: String,
            context: PlatformContext,
        ): Either<BackupError, Boolean> = error("nothing here opens a save dialog")
    }

    private val origin = object : CaptureOrigin {
        override val appVersion = "1.2.3"
        override val platform = BackupPlatform.DESKTOP
    }

    private val vault = BackupVault(
        vault = state,
        archive = RoomArchiveMark(live),
        destination = destination,
        database = live,
        origin = origin,
        files = files,
        clock = clock,
    )

    private val archiveImport = ArchiveImport(
        state = state,
        destination = destination,
        verifier = verifier,
        files = files,
        clock = clock,
    )

    /**
     * No window is ever raised: the picker is [files], and this only exists because the call
     * that reaches it carries one.
     */
    private val context: PlatformContext = PlatformContext(
        object : WindowScope {
            override val window: ComposeWindow get() = error("no picker is raised here")
        }
    )

    @AfterTest
    fun tearDown() {
        live.close()
        (temporaries + folder.listFiles().orEmpty()).forEach { file ->
            DATABASE_FILES.forEach { File(file.absolutePath + it).delete() }
        }
        folder.delete()
    }

    /** What the user entering something looks like from here. */
    private suspend fun enter(title: String): Long =
        live.transactionDao().insert(TransactionEntity(title = title, date = DATE))

    /** A copy of the archive as it stands, taken the way the three triggers take one. */
    private suspend fun capture(): StoredBackup {
        instant += 1.minutes
        return assertIs<CaptureOutcome.Captured>(vault.captureIfNeeded()).copy
    }

    /** A real backup file, sitting where a person's own files sit — outside the vault. */
    private suspend fun backupFileElsewhere(): File {
        val file = temporary("elsewhere-${handedOut++}")
        live.captureInto(
            destinationPath = file.absolutePath,
            appVersion = origin.appVersion,
            platform = origin.platform.id,
        )
        return file
    }

    /** Whatever is actually in the folder, past the name filter the listing applies. */
    private fun filesInFolder(): List<String> =
        folder.listFiles().orEmpty().filter { it.isFile }.map { it.name }.sorted()

    private suspend fun listed(): List<StoredBackup> =
        assertNotNull(destination.list().getOrNull(), "the folder could not be read")

    // --- A file that is not this app's does not land ---------------------------------------

    /**
     * The trap the gate exists for. Removal confirms by reading the file, so a file this app
     * cannot prove it wrote is one it will later refuse to remove — an import that skipped
     * the check would put litter in somebody's own folder that nothing can ever sweep.
     *
     * The folder itself is what is asserted, not the listing: a file landing under a name the
     * listing filters out is exactly the failure that would look like success.
     */
    @Test
    fun `a file that is not a backup of this app does not land`() = runTest {
        state.setOn(true)
        picked = temporary("nonsense").also { it.writeText("this is not a database at all") }

        val outcome = archiveImport.importChosenFile(context)

        assertEquals(
            BackupError.NOT_A_BACKUP,
            assertIs<ImportOutcome.Failed>(outcome).error,
            "the person is told what was wrong with the file they picked",
        )
        assertEquals(emptyList(), filesInFolder(), "and nothing whatever was written")
    }

    /**
     * A copy of this app's own that no longer reads as one is refused too, and the point is
     * that it is refused by *reading* it: the name still conforms, the size is still right,
     * and only opening the file says otherwise.
     *
     * The error is not pinned to a value here. Which of the gate's refusals a half-written
     * file trips is SQLite's business and not this test's; what is asserted is that the
     * person is told, and that the folder is untouched.
     */
    @Test
    fun `a backup file that no longer reads as one does not land`() = runTest {
        state.setOn(true)
        enter("coffee")
        val whole = backupFileElsewhere().readBytes()
        picked = temporary("halved").also { it.writeBytes(whole.copyOf(whole.size / 2)) }

        assertIs<ImportOutcome.Failed>(archiveImport.importChosenFile(context))
        assertEquals(emptyList(), filesInFolder(), "a file nothing could read was kept")
    }

    /** Nothing lands in the vault's destination while the vault is off (design D1). */
    @Test
    fun `nothing is imported while the vault is off`() = runTest {
        enter("coffee")
        picked = backupFileElsewhere()

        assertIs<ImportOutcome.VaultOff>(archiveImport.importChosenFile(context))
        assertEquals(emptyList(), filesInFolder(), "the vault wrote nothing")
    }

    /** A picker somebody closed is not a failure and leaves nothing behind. */
    @Test
    fun `closing the picker imports nothing and reports nothing`() = runTest {
        state.setOn(true)
        picked = null

        assertIs<ImportOutcome.Abandoned>(archiveImport.importChosenFile(context))
        assertEquals(emptyList(), filesInFolder())
    }

    // --- A file that is this app's lands, and is listed -------------------------------------

    /**
     * The name is what decides whether the copy is *there* as far as every screen is
     * concerned: the listing filters by the app's own convention, so a file kept under its
     * original name would be an import that succeeded and showed nothing.
     */
    @Test
    fun `an imported file lands under a name the listing shows`() = runTest {
        state.setOn(true)
        enter("coffee")
        picked = backupFileElsewhere().also { it.renameTo(File(it.parent, "my-own-name.db")) }
        picked = File(picked!!.parent, "my-own-name.db").also { temporaries += it }

        val landed = assertIs<ImportOutcome.Imported>(archiveImport.importChosenFile(context)).copy

        assertTrue(
            isBackupFileName(landed.name),
            "the copy took a name of this app's own, not ${landed.name}",
        )
        assertEquals(
            listOf(landed.name),
            listed().map { it.name },
            "and the history lists it like any other copy",
        )
    }

    /**
     * The report's own defect: nothing inside a file says which install captured it — the
     * same four columns whoever wrote `snapshot_meta` — so a copy this install merely
     * brought in from a picker would otherwise be indistinguishable, once it is sitting in
     * the destination, from one this install captured itself. The name is the one place
     * left to say so, and a restore reads it back to keep from calling such a copy this
     * app's own past (`RestoreClaimsTest`).
     */
    @Test
    fun `an imported file's name says it did not come from this install's own capture`() =
        runTest {
            state.setOn(true)
            enter("coffee")
            picked = backupFileElsewhere()

            val landed = assertIs<ImportOutcome.Imported>(
                archiveImport.importChosenFile(context)
            ).copy

            assertTrue(
                isImportedFileName(landed.name),
                "nothing about ${landed.name} says this copy did not come from a capture",
            )
        }

    /** A copy this install actually captured must never read as one it merely imported. */
    @Test
    fun `a captured file's name is not mistaken for an imported one`() = runTest {
        state.setOn(true)
        enter("coffee")

        val captured = capture()

        assertFalse(
            isImportedFileName(captured.name),
            "a copy this install captured itself was marked as brought in from elsewhere",
        )
    }

    /**
     * The copy that landed is the file, whole. It is asserted by taking it back out of the
     * destination and putting it through the very gate it came in by — which is also the
     * only claim worth making about an imported copy: that a restore would accept it.
     */
    @Test
    fun `an imported copy comes back out of the destination intact`() = runTest {
        state.setOn(true)
        enter("coffee")
        enter("rent")
        picked = backupFileElsewhere()

        val landed = assertIs<ImportOutcome.Imported>(archiveImport.importChosenFile(context)).copy

        val out = temporary("back-out")
        assertTrue(
            assertNotNull(destination.copyOut(landed, out.absolutePath).getOrNull()),
            "the copy could not be read back",
        )

        val verification = assertIs<CandidateVerification.Accepted>(
            verifier.verify(out.absolutePath),
            "the file that landed is not one the restore would take",
        )
        assertEquals(2, verification.counts.transactions, "and it holds what it was made from")
    }

    // --- Nothing about where the app is standing moves ---------------------------------------

    /**
     * "Atual" means the copy the running archive came from. An imported file is not, and
     * marking it would be a lie in the one place this screen is authoritative — so the mark
     * stays on the copy that was actually captured from this archive.
     */
    @Test
    fun `importing does not take the current mark`() = runTest {
        state.setOn(true)
        enter("coffee")
        val captured = capture()

        picked = backupFileElsewhere()
        val landed = assertIs<ImportOutcome.Imported>(archiveImport.importChosenFile(context)).copy

        val mark = assertNotNull(state.observe().value.archiveCopy, "the mark was given up")
        assertTrue(mark.describes(captured), "the mark left the copy the archive came from")
        assertFalse(mark.describes(landed), "the imported file took the mark")
    }

    /**
     * With nothing captured there is nothing to be standing on, and an import does not
     * invent it: an unmarked list says *unknown*, which is the honest answer.
     */
    @Test
    fun `importing into an unmarked history marks nothing`() = runTest {
        state.setOn(true)
        enter("coffee")
        picked = backupFileElsewhere()

        assertIs<ImportOutcome.Imported>(archiveImport.importChosenFile(context))

        assertNull(state.observe().value.archiveCopy, "an imported file was marked as current")
    }

    /**
     * An import is not a capture, and the vault's own record of one does not move for it:
     * the instant of the last successful capture is a fact about this install, and coverage
     * is a claim about the archive — neither is touched by a file arriving from elsewhere.
     */
    @Test
    fun `importing is not recorded as a capture`() = runTest {
        state.setOn(true)
        enter("coffee")
        capture()

        val before = state.observe().value
        picked = backupFileElsewhere()
        assertIs<ImportOutcome.Imported>(archiveImport.importChosenFile(context))
        val after = state.observe().value

        assertEquals(before.lastCapturedAt, after.lastCapturedAt, "the last capture moved")
        assertEquals(before.markAtLastCapture, after.markAtLastCapture, "coverage moved")
    }

    // --- Retention counts it like any other copy ---------------------------------------------

    /**
     * The imported copy occupies a slot in the count and is swept out of it in its turn.
     *
     * It is the oldest file in the folder — it was written first, and it carries the
     * earliest **stamp**, which is what breaks a tie between two files the destination
     * reports at the same instant — so the sweep behind the fifth capture is what reaches
     * it. Nothing here sweeps on its own: retention hangs off a capture that landed
     * (design D10), and the imported file survives untouched until one happens.
     *
     * The stamp and not the name: an imported copy's name carries `imported-` where a
     * captured one carries a date, and that mark outranks every date there is. This test
     * passed over that for as long as it ran against a destination whose own timestamps
     * decide, where the tie never breaks — see [ImportedCopyOrderTest], which asks the
     * ordering directly.
     */
    @Test
    fun `retention counts an imported copy and sweeps it in its turn`() = runTest {
        state.setOn(true)
        state.setRetention(BackupRetention.FIVE)
        enter("coffee")

        picked = backupFileElsewhere()
        val landed = assertIs<ImportOutcome.Imported>(archiveImport.importChosenFile(context)).copy

        repeat(4) { index ->
            enter("entry $index")
            capture()
        }

        assertEquals(
            5,
            listed().size,
            "five copies fit, and the imported one is one of them",
        )
        assertTrue(
            listed().any { it.name == landed.name },
            "nothing swept the imported copy while the limit still held",
        )

        enter("one more")
        capture()

        assertEquals(5, listed().size, "the limit held")
        assertFalse(
            listed().any { it.name == landed.name },
            "the imported copy was not counted, so nothing ever removed it",
        )
    }

    private companion object {
        val DATE = LocalDate(2026, 8, 30)

        /**
         * A database is up to three files while something has it open in write-ahead
         * logging, and the verification opens a candidate with Room.
         */
        val DATABASE_FILES = listOf("", "-wal", "-shm")
    }
}

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun importSeeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
