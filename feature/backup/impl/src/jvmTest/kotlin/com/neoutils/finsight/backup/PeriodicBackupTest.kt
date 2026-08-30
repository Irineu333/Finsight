@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.backup.service.JvmBackupDestination
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
import com.neoutils.finsight.domain.vault.VaultPeriodicBackup
import com.neoutils.finsight.domain.vault.VaultPreventiveBackup
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.feature.backup.api.DestructiveAction
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.russhwolf.settings.MapSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * The periodic trigger: an occasion — the app was opened — and a condition about time.
 *
 * **Every test here differs from its neighbour by the clock and nothing else**, which is the
 * only way to show that what refused was the interval. An archive with something new in it
 * and a vault that is on satisfy every other precondition the vault has, so a copy that does
 * not appear can only have been refused by the elapsed time.
 *
 * The archive and the destination are real, as they are for the vault's own tests: what is
 * being claimed is that opening the app produces files, and a fake destination would answer
 * whatever the test wanted.
 *
 * **The case worth the most is the app closed for months.** The promise is "the first
 * opening after N days", never "every N days" (design D5) — no supported platform lets an
 * app keep the second — and the difference between the two shows up exactly there: sixty
 * intervals went by, and what is owed is one copy, because elapsed time is a condition asked
 * at an occasion and never a backlog of occasions.
 */
class PeriodicBackupTest {

    private val temporaries = mutableListOf<File>()

    private val folder: File = Files.createTempDirectory("finsight-periodic").toFile()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-capture-$name", ".db")
            .also { it.delete(); temporaries += it }

    private fun roomAt(path: String): AppDatabase = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = periodicSeeding(),
    )

    private val live = roomAt(temporary("live").absolutePath)

    private val state = BackupVaultRepository(MapSettings())

    private var instant: Instant = Instant.parse("2026-08-30T10:00:00Z")

    private val clock = object : Clock {
        override fun now(): Instant = instant
    }

    private val destination = JvmBackupDestination(
        ownCopy = OwnCopyCheck(CandidateVerifier(::roomAt)),
        directory = folder,
    )

    private var pathsHandedOut = 0

    private val files = object : BackupFileService {

        override suspend fun newCapturePath(): Either<BackupError, String> =
            temporary("periodic-${pathsHandedOut++}").absolutePath.right()

        override suspend fun discard(path: String) {
            DATABASE_FILES.forEach { File(path + it).delete() }
        }

        override suspend fun copyInChosenFile(context: PlatformContext) =
            error("the trigger never puts a picker in front of anybody")

        override suspend fun copyOutCapturedFile(
            sourcePath: String,
            suggestedName: String,
            context: PlatformContext,
        ) = error("the trigger never puts a picker in front of anybody")
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
        clock = clock,
    )

    private val periodic = VaultPeriodicBackup(state = state, vault = vault, clock = clock)

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

    private suspend fun copies(): List<String> = assertNotNull(
        destination.list().getOrNull(),
        "the destination could not be read",
    ).map { it.name }

    /** The app being opened, which is the only thing this trigger is ever told. */
    private suspend fun opened() = periodic.captureIfDue()

    private fun closedFor(duration: Duration) {
        instant += duration
    }

    /** An app already protected: the vault is on, and one copy is behind it. */
    private suspend fun alreadyProtected(): String {
        state.setOn(true)
        enter("rent")
        opened()
        return assertNotNull(copies().singleOrNull(), "the fixture itself did not capture")
    }

    // ------------------------------------------------------------------------ the interval

    @Test
    fun `an interval that has run out is what an opening captures on`() = runTest {
        val first = alreadyProtected()

        closedFor(FIVE_DAYS)
        enter("groceries")
        opened()

        assertEquals(2, copies().size, "five days over an interval of three owed a copy")
        assertTrue(first in copies(), "and the one that was already there is still there")
    }

    /**
     * The same archive, the same entry, the same vault — one day instead of five. Nothing
     * but the clock says no.
     */
    @Test
    fun `an interval that has not run out is not an occasion at all`() = runTest {
        val first = alreadyProtected()
        val reached = pathsHandedOut

        closedFor(ONE_DAY)
        enter("groceries")
        opened()

        assertEquals(listOf(first), copies(), "a day into an interval of three owes nothing")
        assertEquals(reached, pathsHandedOut, "and it did not even reach for a path")
    }

    /** Nothing yet stands between the archive and its loss, so the first opening captures. */
    @Test
    fun `a vault that has never captured is due the moment it is on`() = runTest {
        state.setOn(true)
        enter("rent")

        opened()

        assertEquals(1, copies().size)
    }

    // --------------------------------------------------------------- months without opening

    /**
     * Sixty intervals go by while the app is closed. The app is opened once, and what that
     * owes is **one** copy — the promise is the first opening after the interval, not one
     * copy per interval that elapsed.
     */
    @Test
    fun `months closed produce one copy on reopening, not one per interval that went by`() =
        runTest {
            val first = alreadyProtected()
            val reached = pathsHandedOut

            closedFor(SIX_MONTHS)
            enter("the first thing entered in months")
            opened()

            assertEquals(2, copies().size, "one opening is one copy, however long it was shut")
            assertEquals(
                reached + 1,
                pathsHandedOut,
                "a copy per elapsed interval would have reached for sixty paths",
            )
            assertTrue(first in copies())
        }

    /**
     * And while it is shut, nothing happens at all: there is no scheduler, no background
     * work and nothing to run — so the instant the screen shows is still the old one, and
     * the app does not get to claim a copy nobody took (design D12).
     */
    @Test
    fun `an app that is never opened captures nothing, and says so`() = runTest {
        val first = alreadyProtected()
        val recorded = state.observe().value.lastCapturedAt

        closedFor(SIX_MONTHS)
        enter("something entered by nobody, since the app is shut")

        assertEquals(listOf(first), copies(), "time passing is not an occasion")
        assertEquals(recorded, state.observe().value.lastCapturedAt)
    }

    // ------------------------------------------------------------------------- the switches

    /**
     * Opening the app is an occasion and not a reason: an interval that has run out with
     * nothing entered since the last copy produces no file, because the copy already there
     * still holds everything the archive does (design D8).
     */
    @Test
    fun `an opening with nothing entered since the last copy produces nothing`() = runTest {
        val first = alreadyProtected()

        closedFor(FIVE_DAYS)
        opened()

        assertEquals(listOf(first), copies())
    }

    /** One trigger goes off without the other: the preventive still captures. */
    @Test
    fun `switching the periodic trigger off leaves the preventive one working`() = runTest {
        val first = alreadyProtected()
        state.setPeriodicOn(false)

        closedFor(FIVE_DAYS)
        enter("groceries")
        opened()
        assertEquals(listOf(first), copies(), "the trigger was switched off")

        VaultPreventiveBackup(state, vault).captureBefore(DestructiveAction.DELETE_TRANSACTION)

        assertEquals(2, copies().size, "the other trigger is not this one's to switch off")
    }

    /** The switch that governs all three is read in one place, and it is not this one. */
    @Test
    fun `a vault that is off captures nothing on any opening`() = runTest {
        enter("rent")

        closedFor(SIX_MONTHS)
        opened()

        assertEquals(emptyList<String>(), copies())
    }

    private companion object {
        val DATE = LocalDate(2026, 8, 30)

        val ONE_DAY = 1.days
        val FIVE_DAYS = 5.days

        /** Sixty times the default interval of three days, in hours, so nothing rounds. */
        val SIX_MONTHS = (180 * 24).hours

        /**
         * A database is up to three files while something has it open in write-ahead
         * logging, and the confirmation opens a copy with Room.
         */
        val DATABASE_FILES = listOf("", "-wal", "-shm")
    }
}

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun periodicSeeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
