package com.neoutils.finsight.backup

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.neoutils.finsight.backup.service.JvmBackupDestination
import com.neoutils.finsight.database.getDatabaseBuilder
import com.neoutils.finsight.database.getRoomDatabase
import com.neoutils.finsight.database.snapshot.CandidateVerifier
import com.neoutils.finsight.database.snapshot.captureInto
import com.neoutils.finsight.domain.model.CURRENCY_SEED
import com.neoutils.finsight.domain.model.CurrencySeeding
import com.neoutils.finsight.domain.model.SeedCurrency
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The first step of the vault: a place the app writes to, reads back and removes from,
 * without a person in front of the screen.
 *
 * The gate is the real one, over real captures, because the single promise that separates a
 * destination from a folder is that it removes only what this app wrote — and a stubbed
 * gate would answer whatever the test told it to, which is the very question being asked.
 *
 * The desktop is where the whole of step one is testable (design D3), and what is tested
 * here is the step itself: the same three operations, over the same reading of a directory,
 * are what the other two platforms carry out.
 */
class JvmBackupDestinationTest {

    private val temporaries = mutableListOf<File>()

    private val vault: File = Files.createTempDirectory("finsight-vault").toFile()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-capture-$name", ".db")
            .also { it.delete(); temporaries += it }

    private fun open(path: String) = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = seeding(),
    )

    private val liveFile = temporary("live")
    private val live = open(liveFile.absolutePath)

    private val gate = OwnCopyCheck(CandidateVerifier(::open))

    private val destination = JvmBackupDestination(
        ownCopy = gate,
        directory = vault,
    )

    @AfterTest
    fun tearDown() {
        live.close()
        (temporaries + vault.listFiles().orEmpty()).forEach { file ->
            DATABASE_FILES.forEach { File(file.absolutePath + it).delete() }
        }
        vault.delete()
    }

    /** A file this app captured, at a path of its own, exactly as a trigger would leave it. */
    private suspend fun capture(name: String = "capture"): String =
        temporary(name).absolutePath.also {
            live.captureInto(destinationPath = it, appVersion = "1.2.3", platform = "desktop")
        }

    private suspend fun putCapture(name: String): StoredBackup = assertNotNull(
        destination.put(capture(name), name).getOrNull(),
        "the copy did not land in the destination",
    )

    private suspend fun names(): List<String> = assertNotNull(
        destination.list().getOrNull(),
        "the destination could not be read",
    ).map { it.name }

    // ------------------------------------------------------------------- writing

    @Test
    fun `a captured file is put in the destination and listed`() = runTest {
        val captured = capture()

        val stored = assertNotNull(
            destination.put(captured, NAME).getOrNull(),
            "the copy did not land in the destination",
        )

        assertEquals(NAME, stored.name)
        assertEquals(File(captured).length(), stored.sizeInBytes, "the whole file went in")
        assertTrue(stored.sizeInBytes > 0, "an empty copy is not a copy")
        assertEquals(listOf(NAME), names())
    }

    /**
     * The captured file belongs to whoever captured it, and the flow that made it is what
     * removes it. A destination that moved it would leave that caller removing nothing.
     */
    @Test
    fun `putting a copy leaves the captured file where it was`() = runTest {
        val captured = capture()

        destination.put(captured, NAME)

        assertTrue(File(captured).exists(), "the temporary is still its owner's to remove")
    }

    /**
     * The app's own folder is empty before the first capture makes it, and empty is the
     * honest answer there. Anything else that stops a listing from being taken is not:
     * design D9 draws the line at *zero copies means could not read*, and a destination
     * that answered an empty list over a folder it never read would have the screen say
     * "no copies yet" and retention count from nothing.
     */
    @Test
    fun `a folder that has not been made yet is empty, and one that cannot be read refuses`() =
        runTest {
            val unmade = File(vault, "not made yet")
            assertEquals(
                emptyList(),
                JvmBackupDestination(ownCopy = gate, directory = unmade).list().getOrNull(),
                "nothing has been captured, so there is nothing in it",
            )

            val notAFolder = File(vault, "a file where the folder should be")
                .apply { writeText("something else entirely") }
            assertTrue(
                JvmBackupDestination(ownCopy = gate, directory = notAFolder).list().isLeft(),
                "a listing that could not be taken was answered as an empty folder",
            )
        }

    /**
     * A vault has nobody to ask about replacing a file, so a name already in use is a copy
     * that has to survive: it is the one that holds what the newer one does not.
     */
    @Test
    fun `a name already in the destination never replaces the copy that holds it`() = runTest {
        val first = putCapture(NAME)
        val firstSize = File(vault, first.name).length()

        val second = assertNotNull(destination.put(capture("second"), NAME).getOrNull())

        assertNotEquals(NAME, second.name, "the second copy was written under a name of its own")
        assertEquals(setOf(NAME, second.name), names().toSet(), "both copies are there")
        assertEquals(firstSize, File(vault, NAME).length(), "the first copy is untouched")
    }

    // ------------------------------------------------------------------- listing

    @Test
    fun `a file that is not named as this app's is not listed`() = runTest {
        putCapture(NAME)
        File(vault, "notes.txt").writeText("the user's own file")

        assertEquals(
            listOf(NAME),
            names(),
            "a destination may be a folder with the user's own things in it",
        )
    }

    @Test
    fun `the newest copy is listed first`() = runTest {
        val older = putCapture(NAME)
        val newer = putCapture(OTHER_NAME)
        File(vault, older.name).setLastModified(EPOCH_DAY)
        File(vault, newer.name).setLastModified(EPOCH_DAY * 2)

        assertEquals(
            listOf(newer.name, older.name),
            names(),
            "retention counts from the newest, and so does the history",
        )
    }

    @Test
    fun `a destination nobody has written to yet lists nothing`() = runTest {
        assertEquals(emptyList<String>(), names())
    }

    /** The history is a reading of the destination, never a record kept somewhere else. */
    @Test
    fun `a copy deleted from outside stops being listed, without an error`() = runTest {
        putCapture(NAME)
        File(vault, NAME).delete()

        assertEquals(emptyList<String>(), names())
    }

    // ------------------------------------------------------------------ removing

    @Test
    fun `a copy this app wrote is removed`() = runTest {
        val stored = putCapture(NAME)

        assertEquals(true, destination.remove(stored).getOrNull(), "the copy was removed")
        assertEquals(emptyList<String>(), names())
        DATABASE_FILES.forEach {
            assertFalse(File(vault, stored.name + it).exists(), "${stored.name}$it is still there")
        }
        assertEquals(
            emptyList(),
            vault.listFiles().orEmpty().map { it.name }.sorted(),
            "the check left its working files in the folder the vault reads",
        )
    }

    /**
     * The promise the whole check exists for. The name says this app wrote the file, and the
     * name is not authority (design D9): a destination the user pointed at holds files of
     * theirs, and retention runs there with nobody watching.
     */
    @Test
    fun `a file that is not a copy of this app is never removed`() = runTest {
        val planted = File(vault, NAME).also { it.writeBytes(ByteArray(IMPOSTOR_BYTES) { 0x7A }) }

        val stored = assertNotNull(
            assertNotNull(destination.list().getOrNull()).singleOrNull(),
            "a file named as this app's is worth looking at, which is all the name is for",
        )

        assertEquals(false, destination.remove(stored).getOrNull(), "the content decided")
        assertTrue(planted.exists(), "the app removed a file it did not write")
    }

    @Test
    fun `a copy the user already deleted is answered as removed`() = runTest {
        val stored = putCapture(NAME)
        File(vault, stored.name).delete()

        assertEquals(
            true,
            destination.remove(stored).getOrNull(),
            "there is nothing left to refuse",
        )
    }

    @Test
    fun `removing one copy leaves the others alone`() = runTest {
        val first = putCapture(NAME)
        val second = putCapture(OTHER_NAME)

        assertEquals(true, destination.remove(first).getOrNull())

        assertEquals(listOf(second.name), names())
    }

    /**
     * The gate that proves a file is this app's *migrates* what it is handed
     * ([com.neoutils.finsight.database.snapshot.CandidateVerifier]), and a refusal is
     * precisely the case in which the copy is still there afterwards. Run over the copy
     * where it lies, the check rewrites a file the app has just decided it may not remove,
     * and leaves everything a database opening puts beside it standing in the folder the
     * vault reads — files nothing lists and nothing else will ever take away.
     */
    @Test
    fun `a copy the check refuses comes back with its bytes untouched`() = runTest {
        val stored = putCapture(NAME)
        val file = File(vault, NAME)
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

    /** The other half of the same rule: nothing of the check's is left in the folder. */
    @Test
    fun `a refused removal leaves nothing beside the copy`() = runTest {
        val stored = putCapture(NAME)
        notThisSchema(File(vault, NAME).absolutePath)

        destination.remove(stored)

        assertEquals(
            listOf(NAME),
            vault.listFiles().orEmpty().map { it.name }.sorted(),
            "the check left its working files in the folder the vault reads",
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

    private companion object {
        const val NAME = "finsight-backup-2026-08-20T14-30-05.db"
        const val OTHER_NAME = "finsight-backup-2026-08-21T14-30-05.db"

        /** A day in milliseconds, only so that two copies carry timestamps that differ. */
        const val EPOCH_DAY = 86_400_000L

        const val IMPOSTOR_BYTES = 4096

        /**
         * A database is up to three files while something has it open in write-ahead
         * logging, and the confirmation opens a copy with Room.
         */
        val DATABASE_FILES = listOf("", "-wal", "-shm")
    }
}

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun seeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
