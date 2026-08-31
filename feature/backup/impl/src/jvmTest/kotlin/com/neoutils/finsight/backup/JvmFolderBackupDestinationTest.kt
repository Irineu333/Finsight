@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup

import com.neoutils.finsight.backup.service.JvmBackupFolder
import com.neoutils.finsight.backup.service.JvmFolderBackupDestination
import com.neoutils.finsight.database.getDatabaseBuilder
import com.neoutils.finsight.database.getRoomDatabase
import com.neoutils.finsight.database.snapshot.CandidateVerifier
import com.neoutils.finsight.database.snapshot.captureInto
import com.neoutils.finsight.domain.model.CURRENCY_SEED
import com.neoutils.finsight.domain.model.CurrencySeeding
import com.neoutils.finsight.domain.model.SeedCurrency
import com.neoutils.finsight.ui.screen.backup.service.BACKUP_FOLDER_NAME
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.russhwolf.settings.MapSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.test.runTest

/**
 * The second rung on the desktop: the same four operations as the first, over a folder
 * somebody chose rather than one the app owns.
 *
 * What is under test is not the four operations — those are the first rung's and are proven
 * in `JvmBackupDestinationTest`. It is the one thing the two rungs disagree about: **what
 * absence means**. A folder that is not there is a folder that was not read, and design D9
 * is explicit that zero copies must never be said over one. The other half of the same rule
 * is that nothing rebuilds the app's own subfolder on the way into a write, because a
 * mountpoint left behind by a detached volume answers every question the way an empty
 * folder does.
 *
 * The gate is the real one over real captures, because "removes only what this app wrote"
 * is a claim about reading files rather than about this code.
 */
class JvmFolderBackupDestinationTest {

    private val temporaries = mutableListOf<File>()

    private val chosen: File = Files.createTempDirectory("finsight-chosen-dest").toFile()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-folder-$name", ".db")
            .also { it.delete(); temporaries += it }

    private fun roomAt(path: String) = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = folderSeeding(),
    )

    private val live = roomAt(temporary("live").absolutePath)

    private val settings = MapSettings()

    private val folder = JvmBackupFolder(settings)

    private val destination = JvmFolderBackupDestination(
        folder = folder,
        ownCopy = OwnCopyCheck(CandidateVerifier(::roomAt)),
    )

    private val own get() = File(chosen, BACKUP_FOLDER_NAME)

    @AfterTest
    fun tearDown() {
        live.close()
        temporaries.forEach { file ->
            DATABASE_FILES.forEach { File(file.absolutePath + it).delete() }
        }
        chosen.deleteRecursively()
    }

    private suspend fun pointAtChosenFolder() {
        assertEquals(true, folder.pointAt(chosen).getOrNull())
    }

    /** A file this app captured, at a path of its own, exactly as a trigger would leave it. */
    private suspend fun capture(name: String = "capture"): String =
        temporary(name).absolutePath.also {
            live.captureInto(destinationPath = it, appVersion = "1.2.3", platform = "desktop")
        }

    private suspend fun put(name: String): StoredBackup = assertNotNull(
        destination.put(capture(name), name).getOrNull(),
        "the copy did not land in the chosen folder",
    )

    // ------------------------------------------------------ the copies go in the folder

    @Test
    fun `a captured file lands in the app's own folder inside the chosen one`() = runTest {
        pointAtChosenFolder()

        val stored = put(NAME)

        assertEquals(NAME, stored.name)
        assertTrue(File(own, NAME).isFile, "the copy is in the folder the person chose")
        assertEquals(listOf(NAME), destination.list().getOrNull()?.map { it.name })
    }

    @Test
    fun `a copy is read back out of the folder`() = runTest {
        pointAtChosenFolder()
        val stored = put(NAME)
        val out = temporary("out")

        assertEquals(true, destination.copyOut(stored, out.absolutePath).getOrNull())

        assertEquals(File(own, NAME).length(), out.length(), "the whole copy came back")
    }

    @Test
    fun `a copy this app wrote is removed, and a file it did not write is refused`() = runTest {
        pointAtChosenFolder()
        val mine = put(NAME)
        val theirs = File(own, OTHER_NAME).apply { writeText("a spreadsheet, not a backup") }

        assertEquals(true, destination.remove(mine).getOrNull())
        assertEquals(
            false,
            destination.remove(StoredBackup(OTHER_NAME, mine.savedAt, theirs.length()))
                .getOrNull(),
        )

        assertFalse(File(own, NAME).exists())
        assertTrue(theirs.exists(), "the folder is the user's, and their files stay in it")
    }

    // ----------------------------------------------------- absence is never emptiness

    /**
     * The rule design D9 is written for. A folder that has gone — unmounted, renamed,
     * deleted — must refuse, because everything downstream reasons from a listing: the
     * history would say "no copies yet" over an archive that is sitting on a disk somewhere,
     * and retention would count from zero.
     */
    @Test
    fun `a folder that is not there refuses the listing instead of answering nothing`() = runTest {
        pointAtChosenFolder()
        put(NAME)

        chosen.deleteRecursively()

        assertTrue(
            destination.list().isLeft(),
            "an unreadable folder answered a list of copies",
        )
    }

    @Test
    fun `a destination nobody has pointed at refuses every operation`() = runTest {
        assertTrue(destination.list().isLeft())
        assertTrue(destination.put(capture(), NAME).isLeft())
        assertTrue(destination.remove(StoredBackup(NAME, NOW, 1)).isLeft())
        assertTrue(destination.copyOut(StoredBackup(NAME, NOW, 1), temporary("x").absolutePath).isLeft())
    }

    /**
     * The other half of design D9, and the one that costs an archive rather than a
     * sentence: a write must never rebuild the app's own subfolder. A network volume that
     * is not mounted leaves a directory behind at its mountpoint, so the chosen folder
     * still answers *yes*; a destination that made its own folder there would take every
     * copy from then on while the real archive sat on the disk that was missing.
     */
    @Test
    fun `a write never rebuilds the app's own folder`() = runTest {
        pointAtChosenFolder()
        own.deleteRecursively()

        assertTrue(destination.put(capture(), NAME).isLeft(), "the write should have refused")

        assertFalse(own.exists(), "the app rebuilt its own folder over a folder that had gone")
        assertTrue(chosen.isDirectory, "the mountpoint stands, which is exactly the trap")
    }

    private companion object {
        const val NAME = "finsight-backup-2026-08-30T14-30-05.db"
        const val OTHER_NAME = "finsight-backup-not-mine.db"

        val NOW = kotlin.time.Instant.fromEpochMilliseconds(0)

        val DATABASE_FILES = listOf("", "-wal", "-shm")
    }
}

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun folderSeeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
