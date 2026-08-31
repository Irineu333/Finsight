@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.backup.service.JvmBackupDestination
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.getDatabaseBuilder
import com.neoutils.finsight.database.getRoomDatabase
import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.database.repository.RoomArchiveMark
import com.neoutils.finsight.database.snapshot.CandidateVerifier
import com.neoutils.finsight.database.snapshot.captureInto
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.model.BackupPlatform
import com.neoutils.finsight.domain.model.CURRENCY_SEED
import com.neoutils.finsight.domain.model.CaptureOrigin
import com.neoutils.finsight.domain.model.CurrencySeeding
import com.neoutils.finsight.domain.model.SeedCurrency
import com.neoutils.finsight.domain.vault.BackupRetention
import com.neoutils.finsight.domain.vault.BackupVault
import com.neoutils.finsight.domain.vault.CaptureOutcome
import com.neoutils.finsight.domain.vault.VaultDestination
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.neoutils.finsight.ui.screen.backup.service.PRE_MIGRATION_BACKUP_NAME
import com.russhwolf.settings.MapSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * The vault as the three triggers will meet it: one entry point that decides whether a copy
 * is owed, takes it, and only then removes what is over the limit.
 *
 * **The archive is real and so is the destination**, because both halves of what is under
 * test are facts about them rather than about this code. Whether a deletion leaves the
 * previous copy sufficient is a claim about `sqlite_sequence` (design D8), and whether
 * retention removes only this app's own files is a claim about a folder with files in it
 * (design D9) — a fake asked either question would answer whatever the test wanted.
 *
 * What is faked is only the app's temporary area, which is where a capture is written
 * before it is handed over, and it is faked so that one test can refuse to provide one.
 *
 * The triggers themselves do not exist yet. What a deletion looks like from here is what it
 * looks like to the vault — the app asking for a copy with nothing entered since the last
 * one — and what an entry looks like is a row in the archive. When sections 3 and 4 land,
 * the scenarios of the spec become expressible end to end; the rule they will rely on is
 * the one proven here.
 */
class BackupVaultTest {

    private val temporaries = mutableListOf<File>()

    private val folder: File = Files.createTempDirectory("finsight-vault").toFile()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-capture-$name", ".db")
            .also { it.delete(); temporaries += it }

    private fun roomAt(path: String): AppDatabase = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = seeding(),
    )

    private val live = roomAt(temporary("live").absolutePath)

    private val settings = MapSettings()

    private val state = BackupVaultRepository(settings)

    private var instant: Instant = Instant.parse("2026-08-30T10:00:00Z")

    private val destination = JvmBackupDestination(
        ownCopy = OwnCopyCheck(CandidateVerifier(::roomAt)),
        directory = folder,
    )

    /** What the app's own temporary area refuses with, when a test wants it to refuse. */
    private var refusePath: BackupError? = null

    private var pathsHandedOut = 0

    private val files = object : BackupFileService {

        override suspend fun newCapturePath(): Either<BackupError, String> =
            refusePath?.left() ?: temporary("vault-${pathsHandedOut++}").absolutePath.right()

        override suspend fun discard(path: String) {
            DATABASE_FILES.forEach { File(path + it).delete() }
        }

        override suspend fun copyInChosenFile(context: PlatformContext) =
            error("the vault never puts a picker in front of anybody")

        override suspend fun copyOutCapturedFile(
            sourcePath: String,
            suggestedName: String,
            context: PlatformContext,
        ) = error("the vault never puts a picker in front of anybody")
    }

    private val vault = BackupVault(
        vault = state,
        archive = RoomArchiveMark(live),
        destination = destination,
        database = live,
        origin = object : CaptureOrigin {
            override val appVersion = "1.2.3"
            override val platform = BackupPlatform.DESKTOP
        },
        files = files,
        clock = object : Clock {
            override fun now(): Instant = instant
        },
    )

    @AfterTest
    fun tearDown() {
        live.close()
        (temporaries + folder.listFiles().orEmpty()).forEach { file ->
            DATABASE_FILES.forEach { File(file.absolutePath + it).delete() }
        }
        folder.delete()
    }

    // ------------------------------------------------------------------- the archive

    /** What the user entering something looks like from here. */
    private suspend fun enter(title: String): Long =
        live.transactionDao().insert(TransactionEntity(title = title, date = DATE))

    private suspend fun remove(id: Long) = live.transactionDao().deleteById(id)

    // -------------------------------------------------------------------- the vault

    private fun turnOn() = state.setOn(true)

    /**
     * One occasion on which a trigger asks the vault for a copy. Time moves first, because
     * two occasions are never the same moment and the name a copy is written under says so.
     */
    private suspend fun asked(): CaptureOutcome {
        instant += 1.minutes
        return vault.captureIfNeeded()
    }

    private suspend fun copies(): List<String> = assertNotNull(
        destination.list().getOrNull(),
        "the destination could not be read",
    ).map { it.name }

    /** A copy put in the destination without the vault's knowledge, and so without a sweep. */
    private suspend fun plant(name: String): String {
        val captured = temporary("planted").absolutePath
        live.captureInto(destinationPath = captured, appVersion = "1.2.3", platform = "desktop")
        return assertNotNull(destination.put(captured, name).getOrNull()).name
    }

    private suspend fun holds(copy: String, ids: List<Long>): Boolean {
        val file = File(folder, copy).also { temporaries += it }
        val database = roomAt(file.absolutePath)
        return try {
            ids.all { database.transactionDao().getById(it) != null }
        } finally {
            database.close()
        }
    }

    // ------------------------------------------------------ the switch governs it all

    @Test
    fun `a vault that is off writes nothing, and asks for nothing to write it into`() = runTest {
        enter("coffee")

        assertEquals(CaptureOutcome.VaultOff, asked())

        assertEquals(emptyList<String>(), copies())
        assertEquals(0, pathsHandedOut, "a vault that is off does not even reach for a path")
    }

    /**
     * The switch governs removal as much as writing. Retention is only ever reached from
     * behind a capture that landed, and a vault that is off never gets that far.
     */
    @Test
    fun `a vault that is off removes nothing either`() = runTest {
        val planted = List(FOUR) { plant(datedName(it)) }

        assertEquals(CaptureOutcome.VaultOff, asked())

        assertEquals(planted.toSet(), copies().toSet())
    }

    // ------------------------------------------------- one copy lasts while nothing is added

    @Test
    fun `the first time it is asked, there is nothing yet that could cover the archive`() =
        runTest {
            turnOn()

            assertIs<CaptureOutcome.Captured>(asked())

            assertEquals(1, copies().size)
        }

    /**
     * Three deletions in a row, with nothing entered between them. The copy taken before
     * the first is the more complete of every pair it is compared with, so the second and
     * third occasions have nothing to protect (design D8).
     */
    @Test
    fun `deletions one after another produce a single copy`() = runTest {
        turnOn()
        val ids = List(THREE) { enter("entry $it") }

        val outcomes = ids.map { id -> asked().also { remove(id) } }

        assertIs<CaptureOutcome.Captured>(outcomes.first())
        assertEquals(
            listOf(CaptureOutcome.AlreadyCovered, CaptureOutcome.AlreadyCovered),
            outcomes.drop(1),
        )
        assertEquals(1, copies().size)
    }

    /**
     * The spec's own scenario, as far as it can be stated without the triggers: what is
     * entered between two deletions is what the second copy exists for, and it is in it.
     */
    @Test
    fun `something entered between two deletions produces a second copy that holds it`() =
        runTest {
            turnOn()
            val first = enter("rent")
            asked()
            remove(first)

            val entered = List(THREE) { enter("entry $it") }
            val second = asked()

            assertIs<CaptureOutcome.Captured>(second)
            assertEquals(2, copies().size)
            assertTrue(holds(second.copy.name, entered), "the copy does not hold what it is for")
        }

    /**
     * Opening the app is an occasion, not a reason. However many of them go by, a copy that
     * still represents the archive is not replaced by a copy of the same thing.
     */
    @Test
    fun `occasions with nothing entered produce no copy at all`() = runTest {
        turnOn()
        enter("coffee")
        val first = asked()

        val later = List(THREE) { asked() }

        assertIs<CaptureOutcome.Captured>(first)
        assertTrue(later.all { it == CaptureOutcome.AlreadyCovered })
        assertEquals(listOf(first.copy.name), copies())
    }

    // ------------------------------------------------------------------------ retention

    /**
     * Six copies against a limit of five. The oldest goes, and what has just been captured
     * is what the destination is left holding.
     */
    @Test
    fun `copies past the limit are removed oldest first, and the newest stays`() = runTest {
        turnOn()
        state.setRetention(BackupRetention.FIVE)

        val taken = List(SIX) { captureSomethingNew("entry $it") }

        assertEquals(FIVE, copies().size)
        assertEquals(taken.drop(1).toSet(), copies().toSet())
        assertTrue(taken.first() !in copies(), "the oldest copy is the one that goes")
    }

    /**
     * The limit the user chose is the limit the sweep applies, in the app's own storage as
     * much as in a folder of theirs: one preference, read by whoever is about to remove
     * something. The vault is in its starting destination here, which is the rung this is
     * about.
     */
    @Test
    fun `the app's own storage keeps as many copies as retention says`() = runTest {
        turnOn()
        state.setRetention(BackupRetention.FIVE)

        List(SIX) { captureSomethingNew("entry $it") }

        assertEquals(FIVE, copies().size)
        assertEquals(VaultDestination.APP_STORAGE, state.observe().value.destination)
    }

    /** And nothing is removed anywhere when the user asks for nothing to be removed. */
    @Test
    fun `retention switched off removes nothing`() = runTest {
        turnOn()
        state.setRetention(BackupRetention.EVERYTHING)

        val taken = List(SIX) { captureSomethingNew("entry $it") }

        assertEquals(taken.toSet(), copies().toSet())
    }

    /**
     * Choosing a smaller number is not a deletion. The sweep runs after a capture that
     * landed and nowhere else (spec: *a remoção MUST NOT ser executada em nenhum outro
     * momento*), so a lowered limit takes effect the next time a copy lands — not under the
     * finger that lowered it, and not on the next opening either.
     */
    @Test
    fun `lowering the limit removes nothing until the next copy lands`() = runTest {
        turnOn()
        state.setRetention(BackupRetention.TWENTY)
        val taken = List(SIX) { captureSomethingNew("entry $it") }

        state.setRetention(BackupRetention.FIVE)

        assertEquals(taken.toSet(), copies().toSet(), "the copies went as the limit was set")
        assertEquals(CaptureOutcome.AlreadyCovered, asked())
        assertEquals(taken.toSet(), copies().toSet(), "an opening swept behind no new copy")

        val next = captureSomethingNew("one more")

        assertEquals(FIVE, copies().size)
        assertTrue(next in copies(), "the copy that triggered the sweep is what stays")
    }

    /** Raising it takes nothing away, and lets the copies pile up to the new number. */
    @Test
    fun `raising the limit keeps what is there and lets more pile up`() = runTest {
        turnOn()
        state.setRetention(BackupRetention.FIVE)
        List(SIX) { captureSomethingNew("entry $it") }
        val held = copies().toSet()

        state.setRetention(BackupRetention.TWENTY)
        val next = captureSomethingNew("one more")

        assertEquals(held + next, copies().toSet())
    }

    @Test
    fun `a capture that fails removes nothing`() = runTest {
        turnOn()
        val planted = List(FOUR) { plant(datedName(it)) }
        enter("coffee")
        refusePath = BackupError.NO_SPACE

        assertEquals(CaptureOutcome.Failed(BackupError.NO_SPACE), asked())

        assertEquals(planted.toSet(), copies().toSet(), "a sweep ran behind a copy that never was")
    }

    /**
     * A sweep declares what it removes, so a copy carried off by retention cannot leave a
     * mark standing on a file that is no longer there. It never reaches the one that
     * covers — that is the copy just captured, so it is the newest, and the smallest limit
     * on offer is five — and this is what says so rather than the ordering being argued.
     */
    @Test
    fun `retention never sweeps the copy that covers the archive`() = runTest {
        turnOn()
        state.setRetention(BackupRetention.FIVE)

        List(SIX) { captureSomethingNew("entry $it") }

        val covering = assertNotNull(
            state.observe().value.archiveCopy,
            "the last capture recorded no copy",
        )
        assertTrue(covering.name in copies(), "the sweep carried off the copy that covers")
        assertEquals(CaptureOutcome.AlreadyCovered, asked(), "and it still covers the archive")
    }

    /**
     * The copy taken before a migration is outside the count and is never swept: the damage
     * it exists to undo is found out days later, by which time the periodic captures would
     * have carried it off (design D10).
     */
    @Test
    fun `the copy taken before a migration survives retention`() = runTest {
        turnOn()
        plant(PRE_MIGRATION_BACKUP_NAME)
        File(folder, PRE_MIGRATION_BACKUP_NAME).setLastModified(LONG_BEFORE)

        state.setRetention(BackupRetention.FIVE)
        List(SIX) { captureSomethingNew("entry $it") }

        assertTrue(
            PRE_MIGRATION_BACKUP_NAME in copies(),
            "the oldest copy was the one that mattered",
        )
        assertEquals(
            FIVE,
            copies().count { it != PRE_MIGRATION_BACKUP_NAME },
            "it was counted against the limit it is supposed to be outside of",
        )
    }

    /**
     * However far over the limit the destination already is, one sweep only ever removes a
     * handful of copies — the safeguard against a lowered limit landing on a destination
     * that was allowed to grow well past it, or a folder adopted already holding far more
     * than this install's own choice, turning into a single sweep that empties most of what
     * was there in one pass. The destination still converges on the limit; it just takes a
     * few sweeps rather than one.
     */
    @Test
    fun `a single sweep never removes the whole excess at once, but retention still converges`() =
        runTest {
            List(FIFTEEN) { plant(plantedName(it)) }
            turnOn()
            state.setRetention(BackupRetention.FIVE)

            assertIs<CaptureOutcome.Captured>(asked())
            assertTrue(
                copies().size > FIVE,
                "a single sweep removed the whole excess of eleven copies in one pass",
            )

            repeat(FOUR) { index -> captureSomethingNew("entry $index") }

            assertEquals(FIVE, copies().size, "retention never converged on the limit it was given")
        }

    /** Enters something and asks — the one shape that reliably produces a copy. */
    private suspend fun captureSomethingNew(title: String): String {
        enter(title)
        return assertIs<CaptureOutcome.Captured>(asked()).copy.name
    }

    private companion object {
        val DATE = LocalDate(2026, 8, 30)

        const val THREE = 3
        const val FOUR = 4
        const val FIVE = 5
        const val SIX = 6
        const val FIFTEEN = 15

        /** Older than anything this test captures, so the ordering is not a coincidence. */
        const val LONG_BEFORE = 86_400_000L

        fun datedName(index: Int) = "finsight-backup-2026-08-2${index}T14-30-05.db"

        /** A name for a copy planted directly, bypassing the vault, with no date to trip on. */
        fun plantedName(index: Int) = "finsight-backup-planted-$index.db"

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
