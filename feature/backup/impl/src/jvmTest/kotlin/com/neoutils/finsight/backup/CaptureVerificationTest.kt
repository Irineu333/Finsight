@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.getDatabaseBuilder
import com.neoutils.finsight.database.getRoomDatabase
import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.database.repository.RoomArchiveMark
import com.neoutils.finsight.database.snapshot.CandidateVerifier
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.model.BackupPlatform
import com.neoutils.finsight.domain.model.CURRENCY_SEED
import com.neoutils.finsight.domain.model.CaptureOrigin
import com.neoutils.finsight.domain.model.CurrencySeeding
import com.neoutils.finsight.domain.model.SeedCurrency
import com.neoutils.finsight.domain.vault.BackupVault
import com.neoutils.finsight.domain.vault.CaptureOutcome
import com.neoutils.finsight.domain.vault.service.BackupDestination
import com.neoutils.finsight.domain.vault.service.BackupFileService
import com.neoutils.finsight.domain.vault.service.OwnCopyCheck
import com.neoutils.finsight.domain.vault.service.StoredBackup
import com.neoutils.finsight.extension.PlatformContext
import com.russhwolf.settings.MapSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * The report's own fourth defect: `put` returning success is the whole proof a capture is
 * good, on every call site but this one.
 *
 * **Why this needs its own destination rather than a real one.** Four of the five rungs this
 * app writes to move a copy into place only once every byte of it is there — a truncated or
 * corrupted write never lands under the name the history shows (`copyIntoPlace` and its
 * Android and iOS equivalents). Android's SAF folder rung is the one exception:
 * `AndroidFolderBackupDestination.put` creates the document under its final name directly,
 * and its own comment says why — "there is no rename into place to lean on, so a process
 * killed mid-write still leaves one [truncated file]". [FlakyDestination] is that shape,
 * without the platform code it needs: `put` reports success while writing whatever
 * [corruptNextWrite] says instead of the captured content, which is exactly what a provider
 * does to a document whose write was cut short.
 *
 * The archive, the gate and the vault around it are all real; only the one act this suite
 * exists to catch is faked.
 */
class CaptureVerificationTest {

    private val temporaries = mutableListOf<File>()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-verify-$name", ".db")
            .also { it.delete(); temporaries += it }

    private fun roomAt(path: String): AppDatabase = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = verificationSeeding(),
    )

    private val live = roomAt(temporary("live").absolutePath)
    private val verifier = CandidateVerifier(::roomAt)
    private val ownCopy = OwnCopyCheck(verifier)

    private val folder: File = Files.createTempDirectory("finsight-verify").toFile()

    private val state = BackupVaultRepository(MapSettings())

    private var instant: Instant = Instant.parse("2026-08-30T10:00:00Z")

    private var handedOut = 0

    private val files = object : BackupFileService {
        override suspend fun newCapturePath(): Either<BackupError, String> =
            temporary("out-${handedOut++}").absolutePath.right()

        override suspend fun discard(path: String) {
            DATABASE_FILES.forEach { File(path + it).delete() }
        }

        override suspend fun copyInChosenFile(context: PlatformContext) =
            error("nothing here opens a picker")

        override suspend fun copyOutCapturedFile(
            sourcePath: String,
            suggestedName: String,
            context: PlatformContext,
        ) = error("nothing here opens a save dialog")
    }

    /** Set for the one capture that must land as garbage instead of the archive. */
    private var corruptNextWrite = false

    private val destination = FlakyDestination(folder, ownCopy) { corruptNextWrite }

    private val vault = BackupVault(
        vault = state,
        archive = RoomArchiveMark(live),
        destination = destination,
        database = live,
        origin = object : CaptureOrigin {
            override val appVersion = "1.2.3"
            override val platform = BackupPlatform.ANDROID
        },
        files = files,
        clock = object : Clock {
            override fun now(): Instant = instant
        },
        verifier = verifier,
    )

    @AfterTest
    fun tearDown() {
        live.close()
        (temporaries + folder.listFiles().orEmpty()).forEach { file ->
            DATABASE_FILES.forEach { File(file.absolutePath + it).delete() }
        }
        folder.delete()
    }

    private suspend fun enter(title: String): Long =
        live.transactionDao().insert(TransactionEntity(title = title, date = DATE))

    private suspend fun asked(): CaptureOutcome {
        instant += 1.minutes
        return vault.captureIfNeeded()
    }

    private fun namesInFolder(): List<String> = folder.listFiles().orEmpty().map { it.name }

    @Test
    fun `a capture whose landed bytes are not the archive is reported as failed, not captured`() =
        runTest {
            state.setOn(true)
            enter("coffee")
            corruptNextWrite = true

            val outcome = asked()

            assertIs<CaptureOutcome.Failed>(
                outcome,
                "the write reporting success was taken as the whole proof the copy is good",
            )
        }

    /**
     * The removal gate every kept copy goes through is the same gate that just rejected
     * this one, and for the two reasons that matter most here — the bytes are not a
     * database at all, or they are and are damaged — it cannot prove garbage is this app's
     * own file, so it refuses to delete it (`OwnCopyCheck`). That is the identical refusal
     * it gives a stranger's file sitting in a folder the person chose, and it is the right
     * refusal here too: what must not happen already did not — the capture was not
     * reported as a success.
     */
    @Test
    fun `a copy the gate cannot prove is this app's own may be left standing, and still is not a success`() =
        runTest {
            state.setOn(true)
            enter("coffee")
            corruptNextWrite = true

            val outcome = asked()

            assertIs<CaptureOutcome.Failed>(outcome)
            assertEquals(
                1,
                namesInFolder().size,
                "the corrupted file was expected to still be on disk, refused by the same " +
                    "gate that refuses a stranger's file",
            )
        }

    @Test
    fun `a good capture is unaffected by the same verification`() = runTest {
        state.setOn(true)
        enter("coffee")

        val outcome = asked()

        assertIs<CaptureOutcome.Captured>(outcome)
        assertEquals(listOf(outcome.copy.name), namesInFolder())
    }

    @Test
    fun `a capture that lands well is never mistaken for one that did not`() = runTest {
        state.setOn(true)
        enter("coffee")
        corruptNextWrite = false

        val outcome = asked()

        assertFalse(outcome is CaptureOutcome.Failed, "a good capture was told apart as bad")
    }

    private companion object {
        val DATE = LocalDate(2026, 8, 30)

        val DATABASE_FILES = listOf("", "-wal", "-shm")
    }
}

/**
 * The one destination rung that has no rename to lean on, modeled directly.
 *
 * [put] writes whatever [corrupt] says at the moment it is called instead of the captured
 * file, and still answers success — a provider does exactly this to a document whose write
 * was cut short, because the document already carries the name the app recognises before a
 * single byte of it has landed.
 */
private class FlakyDestination(
    private val directory: File,
    private val ownCopy: OwnCopyCheck,
    private val corrupt: () -> Boolean,
) : BackupDestination {

    override suspend fun put(
        capturedPath: String,
        name: String,
    ): Either<BackupError, StoredBackup> {
        val target = File(directory, name)
        if (corrupt()) {
            target.writeBytes(byteArrayOf(1, 2, 3))
        } else {
            File(capturedPath).copyTo(target, overwrite = true)
        }
        return target.asStoredBackup().right()
    }

    override suspend fun list(): Either<BackupError, List<StoredBackup>> =
        directory.listFiles().orEmpty()
            .filter { it.isFile }
            .map { it.asStoredBackup() }
            .right()

    override suspend fun copyOut(
        backup: StoredBackup,
        destinationPath: String,
    ): Either<BackupError, Boolean> {
        val file = File(directory, backup.name)
        if (!file.exists()) return false.right()
        file.copyTo(File(destinationPath), overwrite = true)
        return true.right()
    }

    /** The same proof every rung asks for before it removes anything (design D9). */
    override suspend fun remove(backup: StoredBackup): Either<BackupError, Boolean> {
        val file = File(directory, backup.name)
        if (!file.exists()) return true.right()

        val scratch = File.createTempFile("finsight-verify-scratch", ".db").also { it.delete() }
        return try {
            file.copyTo(scratch, overwrite = true)
            if (!ownCopy.confirms(scratch.absolutePath)) return false.right()
            file.delete()
            true.right()
        } finally {
            scratch.delete()
        }
    }

    private fun File.asStoredBackup() = StoredBackup(
        name = name,
        savedAt = Instant.fromEpochMilliseconds(lastModified()),
        sizeInBytes = length(),
    )
}

private fun verificationSeeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
