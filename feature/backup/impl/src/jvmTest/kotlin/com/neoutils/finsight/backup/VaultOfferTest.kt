@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.backup.service.JvmBackupDestination
import com.neoutils.finsight.database.AppDatabase
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
import com.neoutils.finsight.domain.vault.VaultInterval
import com.neoutils.finsight.domain.vault.VaultOfferOnce
import com.neoutils.finsight.domain.vault.VaultSwitch
import com.neoutils.finsight.domain.vault.label
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.russhwolf.settings.MapSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * The offer beside a destructive confirmation: what it costs to accept, and the fact that
 * it is put once.
 *
 * The asserting is against the vault rather than against the return value, because the
 * requirement is what accepting *does*: an offer that said yes and left the vault off, or
 * one that armed a single trigger, would satisfy "it returned terms" and protect nobody.
 *
 * Accepting also takes the copy that turning the vault on means, so the vault here is the
 * real one over a real destination. What that copy is worth beside a deletion — one file
 * and not two — is [VaultSwitchTest]'s.
 */
class VaultOfferTest {

    private val temporaries = mutableListOf<File>()

    private val folder: File = Files.createTempDirectory("finsight-offer").toFile()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-capture-$name", ".db")
            .also { it.delete(); temporaries += it }

    private fun roomAt(path: String): AppDatabase = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = offerSeeding(),
    )

    private val live = roomAt(temporary("live").absolutePath)

    private val vault = BackupVaultRepository(MapSettings())

    private val destination = JvmBackupDestination(
        ownCopy = OwnCopyCheck(CandidateVerifier(::roomAt)),
        directory = folder,
    )

    private val files = object : BackupFileService {

        override suspend fun newCapturePath(): Either<BackupError, String> =
            temporary("offer").absolutePath.right()

        override suspend fun discard(path: String) {
            DATABASE_FILES.forEach { File(path + it).delete() }
        }

        override suspend fun copyInChosenFile(context: PlatformContext) =
            error("the offer never puts a picker in front of anybody")

        override suspend fun copyOutCapturedFile(
            sourcePath: String,
            suggestedName: String,
            context: PlatformContext,
        ) = error("the offer never puts a picker in front of anybody")
    }

    private val switch = VaultSwitch(
        state = vault,
        vault = BackupVault(
            vault = vault,
            archive = RoomArchiveMark(live),
            destination = destination,
            database = live,
            origin = object : CaptureOrigin {
                override val appVersion = "1.2.3"
                override val platform = BackupPlatform.DESKTOP
            },
            files = files,
            clock = object : Clock {
                override fun now(): Instant = INSTANT
            },
        ),
    )

    private val offer = VaultOfferOnce(vault = vault, switch = switch)

    @AfterTest
    fun tearDown() {
        live.close()
        (temporaries + folder.listFiles().orEmpty()).forEach { file ->
            DATABASE_FILES.forEach { File(file.absolutePath + it).delete() }
        }
        folder.delete()
    }

    @Test
    fun `one yes turns the whole vault on`() = runTest {
        val terms = assertNotNull(offer.offerOnce(), "a vault that is off is offered")

        assertFalse(vault.observe().value.isOn, "showing the offer decides nothing")

        terms.accept()

        val state = vault.observe().value
        assertTrue(state.isOn, "accepting turns the vault on, not one copy")
        assertTrue(state.isPeriodicOn, "and every trigger with it (design D1)")
        assertTrue(state.isPreventiveOn)
    }

    /**
     * Somebody who said no is not asked again every time they destroy something. What must
     * not happen twice is the asking, so it is the showing that is recorded and not the
     * answer.
     */
    @Test
    fun `the offer is made once, whatever the answer was`() {
        assertNotNull(offer.offerOnce(), "the first time")

        assertNull(offer.offerOnce(), "and not again, though nobody ever accepted")
        assertFalse(vault.observe().value.isOn)
    }

    /**
     * Five confirmations across three features carry the offer, and the person meets
     * whichever they reach first. The gate is the vault's own state, so the second, third
     * and fifth sheet find nothing to show however many of them are built — one gate, and
     * never one per confirmation.
     */
    @Test
    fun `the offer rides on whichever of the five comes first, and on none of the rest`() {
        val confirmations = List(5) { VaultOfferOnce(vault = vault, switch = switch) }

        val offered = confirmations.mapNotNull { it.offerOnce() }

        assertEquals(1, offered.size, "the offer was made more than once, or not at all")
        assertTrue(vault.observe().value.wasOffered, "and the asking is what is recorded")
    }

    /**
     * A confirmation reached after the vault is on has nothing to offer either, and that is
     * the same question rather than a second one: there is nothing left to turn on.
     */
    @Test
    fun `accepting on the first stops every later confirmation from offering`() = runTest {
        assertNotNull(offer.offerOnce()).accept()

        assertNull(VaultOfferOnce(vault = vault, switch = switch).offerOnce())
    }

    @Test
    fun `a vault that is already on has nothing to offer`() {
        vault.setOn(true)

        assertNull(offer.offerOnce())
    }

    /**
     * The sentence beside the box names the wait in force, because accepting buys that
     * wait from now on and not this one copy.
     */
    @Test
    fun `the terms state the interval in force`() {
        vault.setInterval(7.days)

        assertEquals(
            VaultInterval.SEVEN_DAYS.label,
            assertNotNull(offer.offerOnce()).intervalLabel,
        )
    }

    private companion object {
        val INSTANT: Instant = Instant.parse("2026-08-30T10:00:00Z")

        /**
         * A database is up to three files while something has it open in write-ahead
         * logging, and reading a copy back opens it with Room.
         */
        val DATABASE_FILES = listOf("", "-wal", "-shm")
    }
}

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun offerSeeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
