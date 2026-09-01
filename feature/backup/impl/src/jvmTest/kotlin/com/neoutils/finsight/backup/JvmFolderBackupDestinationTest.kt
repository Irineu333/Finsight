@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.backup.service.JvmBackupFolder
import com.neoutils.finsight.backup.service.JvmFolderBackupDestination
import com.neoutils.finsight.database.getDatabaseBuilder
import com.neoutils.finsight.database.getRoomDatabase
import com.neoutils.finsight.database.snapshot.CandidateVerifier
import com.neoutils.finsight.database.snapshot.captureInto
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.model.CURRENCY_SEED
import com.neoutils.finsight.domain.model.CurrencySeeding
import com.neoutils.finsight.domain.model.SeedCurrency
import com.neoutils.finsight.domain.vault.service.BackupFileService
import com.neoutils.finsight.domain.vault.service.OwnCopyCheck
import com.neoutils.finsight.domain.vault.service.StoredBackup
import com.neoutils.finsight.extension.PlatformContext
import com.russhwolf.settings.MapSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.test.runTest

/**
 * The second rung on the desktop: the same four operations as the first, over a folder
 * somebody chose rather than one the app owns. The copies go straight into that folder —
 * there is no subfolder of the app's own inside it.
 *
 * What is under test is not the four operations — those are the first rung's and are proven
 * in `JvmBackupDestinationTest`. It is the one thing the two rungs disagree about: **what
 * absence means**. A folder that is not there is a folder that was not read, and design D9
 * is explicit that zero copies must never be said over one. The other half of the same rule
 * is that nothing rebuilds the chosen folder on the way into a write.
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

    /**
     * The app's own temporary area, which is where the gate reads a copy: the folder under
     * test is the person's, and nothing of this app's working files may land in it.
     */
    private val files = object : BackupFileService {

        override suspend fun newCapturePath(): Either<BackupError, String> =
            temporary("scratch").absolutePath.right()

        override suspend fun discard(path: String) {
            DATABASE_FILES.forEach { File(path + it).delete() }
        }

        override suspend fun copyInChosenFile(context: PlatformContext) =
            error("no picker is raised here")

        override suspend fun copyOutCapturedFile(
            sourcePath: String,
            suggestedName: String,
            context: PlatformContext,
        ) = error("no picker is raised here")
    }

    private val destination = JvmFolderBackupDestination(
        folder = folder,
        ownCopy = OwnCopyCheck(CandidateVerifier(::roomAt)),
        files = files,
    )

    @AfterTest
    fun tearDown() {
        live.close()
        chosen.setWritable(true)
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
    fun `a captured file lands directly in the chosen folder`() = runTest {
        pointAtChosenFolder()

        val stored = put(NAME)

        assertEquals(NAME, stored.name)
        assertTrue(File(chosen, NAME).isFile, "the copy is in the folder the person chose")
        assertEquals(listOf(NAME), destination.list().getOrNull()?.map { it.name })
    }

    @Test
    fun `a copy is read back out of the folder`() = runTest {
        pointAtChosenFolder()
        val stored = put(NAME)
        val out = temporary("out")

        assertEquals(true, destination.copyOut(stored, out.absolutePath).getOrNull())

        assertEquals(File(chosen, NAME).length(), out.length(), "the whole copy came back")
    }

    @Test
    fun `a copy this app wrote is removed, and a file it did not write is refused`() = runTest {
        pointAtChosenFolder()
        val mine = put(NAME)
        val theirs = File(chosen, OTHER_NAME).apply { writeText("a spreadsheet, not a backup") }

        assertEquals(true, destination.remove(mine).getOrNull())
        assertEquals(
            false,
            destination.remove(StoredBackup(OTHER_NAME, mine.savedAt, theirs.length()))
                .getOrNull(),
        )

        assertFalse(File(chosen, NAME).exists())
        assertTrue(theirs.exists(), "the folder is the user's, and their files stay in it")
    }

    // -------------------------------------------------- a refusal leaves the folder alone

    /**
     * The gate that decides whether a file is this app's *migrates* what it is handed, so
     * what it is handed must be a copy the caller is willing to lose
     * ([com.neoutils.finsight.database.snapshot.CandidateVerifier]). A copy sitting in
     * somebody's own folder is not that, and a refusal is exactly the case in which it is
     * not removed afterwards either — so a check run over it in place rewrites a file the
     * app has just decided it may not touch.
     */
    @Test
    fun `a copy the check refuses comes back with its bytes untouched`() = runTest {
        pointAtChosenFolder()
        val stored = put(NAME)
        val file = File(chosen, NAME)
        notThisSchema(file.absolutePath)
        val before = file.readBytes()

        assertEquals(
            false,
            destination.remove(stored).getOrNull(),
            "the check should have refused a file whose schema is not this one's",
        )

        assertTrue(file.exists(), "a refused removal took the file anyway")
        assertContentEquals(before, file.readBytes(), "the check rewrote the copy it refused")
    }

    /**
     * The other half of the same rule, and the one nothing downstream can clean up: the
     * folder is the person's, nothing in the app lists what is not a copy, and nothing ever
     * removes it. A database opening leaves up to three files beside the one it opened.
     */
    @Test
    fun `a refused removal leaves nothing beside the copy`() = runTest {
        pointAtChosenFolder()
        val stored = put(NAME)
        notThisSchema(File(chosen, NAME).absolutePath)

        destination.remove(stored)

        assertEquals(
            listOf(NAME),
            chosen.listFiles().orEmpty().map { it.name }.sorted(),
            "the check left its working files in the person's own folder",
        )
    }

    /**
     * Why a copy is never written under its final name until every byte is there. A file cut
     * short carries a name the app recognises, so it is listed, and retention counts it
     * inside the window it keeps — and this is the other half: it can never be taken away
     * again. A truncated database reads as corrupt, corruption is not proof a file is this
     * app's, and the refusal is permanent. One of them costs one real copy at every capture,
     * for as long as the destination exists.
     */
    @Test
    fun `a copy cut short can never be removed again`() = runTest {
        pointAtChosenFolder()
        val stored = put(NAME)
        val file = File(chosen, NAME)
        file.writeBytes(file.readBytes().copyOf(file.length().toInt() / 2))

        assertEquals(
            false,
            destination.remove(stored).getOrNull(),
            "a half-written copy could be swept, so writing one would cost nothing",
        )
        assertTrue(file.exists(), "and it is still there, holding a place in the retention")
    }

    /** A removal that goes through leaves the folder as it found it, minus the copy. */
    @Test
    fun `a removal that goes through leaves nothing behind`() = runTest {
        pointAtChosenFolder()
        val stored = put(NAME)

        assertEquals(true, destination.remove(stored).getOrNull())

        assertEquals(
            emptyList(),
            chosen.listFiles().orEmpty().map { it.name }.sorted(),
            "the check left its working files in the person's own folder",
        )
    }

    /**
     * A copy of this app's own making whose schema identity is another one's — the file
     * that gets past every layer that reads without writing and is refused by the layer
     * that migrates.
     */
    private fun notThisSchema(path: String) {
        val connection = BundledSQLiteDriver().open(path)
        try {
            connection.execSQL("UPDATE `room_master_table` SET `identity_hash` = 'elsewhere'")
        } finally {
            connection.close()
        }
    }

    /**
     * `false` is the destination saying *this file is not one I wrote*, and the screen turns
     * it into exactly that sentence. A file the app proved was its own and then could not
     * unlink is a failure of the machine and says nothing whatever about the content, so it
     * leaves as one — the alternative is telling somebody their own backup is a stranger's
     * file because a folder went read-only.
     */
    @Test
    fun `a removal the file system refused is a failure and not a verdict`() = runTest {
        pointAtChosenFolder()
        val stored = put(NAME)
        assertTrue(chosen.setWritable(false), "the folder could not be made read-only")

        val outcome = destination.remove(stored)

        assertTrue(
            outcome.isLeft(),
            "a deletion that did not happen was answered as a file this app did not write",
        )
        assertTrue(File(chosen, NAME).exists(), "the copy is still there, which is the point")
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

    /**
     * The other side of the same rule, and the state every chosen folder is in between being
     * pointed at and the first copy landing in it.
     *
     * Absence may be said once it has been established, and here it has: the chosen folder
     * is there, [JvmFolderBackupDestination] has just confirmed it, and a directory that
     * answers with no entries holds none. Refusing this would tell somebody the folder they
     * had just chosen could not be read — and it is the same answer the Android rung gives,
     * along a road of its own, so the two rungs cannot start disagreeing about what an empty
     * folder is.
     */
    @Test
    fun `a folder that was just chosen is empty rather than unreadable`() = runTest {
        pointAtChosenFolder()

        assertEquals(
            emptyList(),
            destination.list().getOrNull(),
            "a folder that was just chosen and is still empty refused the listing",
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
     * sentence: a write must never rebuild the folder somebody chose. A destination that did
     * would, on the day a network volume is not mounted and leaves a directory behind at its
     * mountpoint, write copies into that local stub while the archive it is supposed to be
     * adding to sits on the disk that is missing (see [JvmBackupFolder]'s own comment on why
     * a plain path cannot tell the two apart once there is no marker subfolder to check).
     */
    @Test
    fun `a write into a folder that has gone refuses and rebuilds nothing`() = runTest {
        pointAtChosenFolder()
        chosen.deleteRecursively()

        assertTrue(destination.put(capture(), NAME).isLeft(), "the write should have refused")

        assertFalse(chosen.exists(), "the app rebuilt a folder somebody had taken away")
    }

    private companion object {
        const val NAME = "finsight-backup-2026-08-30T14-30-05.db"
        const val OTHER_NAME = "finsight-backup-not-mine.db"

        val NOW = kotlin.time.Instant.fromEpochMilliseconds(0)

        val DATABASE_FILES = listOf("", "-wal", "-shm", ".lck")
    }
}

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun folderSeeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
