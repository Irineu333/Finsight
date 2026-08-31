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
import com.neoutils.finsight.domain.vault.StandingVaultOffer
import com.neoutils.finsight.domain.vault.VaultInterval
import com.neoutils.finsight.domain.vault.VaultSwitch
import com.neoutils.finsight.domain.vault.label
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.feature.backup.api.VaultOfferState
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
 * The offer beside a destructive confirmation: what it costs to accept, what a refusal
 * changes, and the one thing that ends it.
 *
 * The asserting is against the vault rather than against the return value, because the
 * requirement is what accepting *does*: an offer that said yes and left the vault off, or
 * one that armed a single trigger, would satisfy "it returned terms" and protect nobody.
 *
 * The refusals are asserted through [VaultOfferState], because that is where the answer is
 * given: the two together are the rule — the offer states what was answered last time, and
 * the box acts on it before the destructive action and records it after being left empty.
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

    private val offer = StandingVaultOffer(vault = vault, switch = switch)

    /** A confirmation going up: it asks for the offer and gets the box that goes with it. */
    private fun confirmation() = VaultOfferState(StandingVaultOffer(vault, switch))

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
        val terms = assertNotNull(offer.offer(), "a vault that is off is offered")

        assertFalse(vault.observe().value.isOn, "showing the offer decides nothing")

        terms.accept()

        val state = vault.observe().value
        assertTrue(state.isOn, "accepting turns the vault on, not one copy")
        assertTrue(state.isPeriodicOn, "and every trigger with it (design D1)")
        assertTrue(state.isPreventiveOn)
    }

    /**
     * The box is ticked the first time, because the offer is made rather than merely
     * displayed — and nothing is recorded by showing it. Somebody who reads the sheet and
     * cancels the deletion has answered nothing.
     */
    @Test
    fun `the first confirmation offers, ticked, and records nothing by asking`() {
        val first = confirmation()

        assertNotNull(first.terms, "a vault that is off is offered")
        assertTrue(first.isAccepted.value, "the offer is made, not merely displayed")
        assertFalse(vault.observe().value.wasDeclined, "showing it is not an answer")
    }

    /**
     * The offer does not disappear after a no, and that is the whole point of it: the switch
     * lives on a screen somebody has to go looking for, so a deletion is the only place the
     * vault is met by the people who most need it. What the refusal buys is the tone — the
     * box arrives empty, and the sentence beside it is a reminder rather than a proposal.
     */
    @Test
    fun `a confirmation after a refusal still offers, unticked`() = runTest {
        val first = confirmation()
        first.setAccepted(false)
        first.settle()

        assertTrue(vault.observe().value.wasDeclined, "going ahead unticked is the answer")

        val second = confirmation()
        val terms = assertNotNull(second.terms, "the offer stands while the vault is off")

        assertTrue(terms.wasDeclined, "and it says which answer it is carrying")
        assertFalse(second.isAccepted.value, "so the box arrives with the answer given last")
        assertFalse(vault.observe().value.isOn, "and nothing was turned on behind anybody")
    }

    /**
     * The refusal is a fact and not a countdown: the third and fourth deletions carry the
     * same reminder as the second, because there is exactly one thing recorded about it.
     */
    @Test
    fun `every later confirmation carries the same reminder`() = runTest {
        confirmation().also { it.setAccepted(false) }.settle()

        repeat(3) {
            val next = confirmation()
            assertNotNull(next.terms).let { terms ->
                assertTrue(terms.wasDeclined, "the reminder is not spent by being shown")
            }
            next.settle()
        }

        assertNotNull(confirmation().terms, "the offer is still there")
    }

    /**
     * A box left ticked can follow a refusal. Nothing about having said no once narrows what
     * accepting does: it is the same offer, and it turns the whole vault on.
     */
    @Test
    fun `the reminder can still be accepted, and turns the whole vault on`() = runTest {
        confirmation().also { it.setAccepted(false) }.settle()

        val second = confirmation()
        second.setAccepted(true)
        second.settle()

        val state = vault.observe().value
        assertTrue(state.isOn, "accepting a reminder is accepting")
        assertTrue(state.isPeriodicOn, "and every trigger with it (design D1)")
        assertTrue(state.isPreventiveOn)
    }

    /**
     * Five confirmations across three features carry the offer, and the person meets
     * whichever they reach first. Every one of them offers, because the only thing that
     * stops the offer is there being nothing left to turn on.
     */
    @Test
    fun `all five of the confirmations offer while the vault is off`() {
        val confirmations = List(5) { StandingVaultOffer(vault = vault, switch = switch) }

        val offered = confirmations.mapNotNull { it.offer() }

        assertEquals(5, offered.size, "a confirmation was left with nothing to offer")
    }

    /**
     * A confirmation reached after the vault is on has nothing to offer, and that is the
     * one thing that ends the offer: there is nothing left to turn on.
     */
    @Test
    fun `accepting on the first stops every later confirmation from offering`() = runTest {
        assertNotNull(offer.offer()).accept()

        assertNull(StandingVaultOffer(vault = vault, switch = switch).offer())
    }

    @Test
    fun `a vault that is already on has nothing to offer`() {
        vault.setOn(true)

        assertNull(offer.offer())
    }

    /**
     * A sheet that was read and dismissed carries no answer. Only a destructive action that
     * actually went ahead past an empty box is a refusal — so the next confirmation still
     * arrives ticked.
     */
    @Test
    fun `a confirmation that is abandoned records nothing`() {
        confirmation().setAccepted(false)

        assertFalse(vault.observe().value.wasDeclined, "nothing was gone ahead with")
        assertTrue(confirmation().isAccepted.value, "so the next one is still a proposal")
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
            assertNotNull(offer.offer()).intervalLabel,
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
