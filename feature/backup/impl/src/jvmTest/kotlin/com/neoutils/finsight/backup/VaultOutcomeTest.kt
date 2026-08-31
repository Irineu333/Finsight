package com.neoutils.finsight.backup

import com.neoutils.finsight.domain.vault.BackupRetention
import com.neoutils.finsight.domain.vault.VaultInterval
import com.neoutils.finsight.domain.vault.VaultState
import com.neoutils.finsight.ui.modal.vaultSettings.VaultOutcome
import com.neoutils.finsight.ui.modal.vaultSettings.vaultOutcome
import com.neoutils.finsight.ui.screen.backup.VaultCopies
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * What the settings sheet concludes from the vault, in each of the four positions the two
 * triggers can be left in.
 *
 * The point of the file is the asymmetry between the two readings the sheet makes. The room
 * a limit takes is that limit and the average of the copies already written, and it holds
 * wherever the limit does — because the sweep is reached from every capture that lands,
 * whichever trigger produced it. A span of history and a rate per month are the readings
 * that also need a wait, and a wait is in force only while the trigger that consults it is.
 *
 * Conflating the two is the mistake this guards: it costs the person who keeps the periodic
 * trigger off the only statement of how much room their backups take, and it costs whoever
 * chooses to keep everything the warning that says so.
 */
class VaultOutcomeTest {

    private val fourMegabytes = 4L * 1_024 * 1_024

    private fun vault(
        periodic: Boolean = true,
        preventive: Boolean = true,
        retention: BackupRetention = BackupRetention.TEN,
        interval: VaultInterval = VaultInterval.THREE_DAYS,
    ) = VaultState(
        isOn = true,
        isPeriodicOn = periodic,
        isPreventiveOn = preventive,
        retention = retention,
        interval = interval.duration,
    )

    private fun captured(count: Int = 10, each: Long = 4L * 1_024 * 1_024) =
        VaultCopies(count = count, totalBytes = each * count)

    // ------------------------------------------------------------------ the four states

    @Test
    fun `both triggers on state the span and the room`() {
        val outcome = vaultOutcome(vault(periodic = true, preventive = true), captured())

        val keeps = assertIs<VaultOutcome.Keeps>(outcome)
        assertEquals(10, keeps.copies)
        assertEquals(30, keeps.historyDays, "three days ten deep is a month of history")
        assertEquals(fourMegabytes, keeps.eachBytes)
    }

    @Test
    fun `the preventive trigger alone still states the room`() {
        val outcome = vaultOutcome(vault(periodic = false, preventive = true), captured())

        val keeps = assertIs<VaultOutcome.Keeps>(outcome)
        assertEquals(10, keeps.copies, "the limit governs the copies the preventive trigger takes")
        assertEquals(fourMegabytes, keeps.eachBytes, "what ten copies take is not a matter of waiting")
        assertNull(keeps.historyDays, "nothing goes by the clock, so no span can be claimed")
    }

    @Test
    fun `the periodic trigger alone states both, as it always did`() {
        val outcome = vaultOutcome(vault(periodic = true, preventive = false), captured())

        val keeps = assertIs<VaultOutcome.Keeps>(outcome)
        assertEquals(30, keeps.historyDays)
        assertEquals(fourMegabytes, keeps.eachBytes)
    }

    @Test
    fun `both triggers off still state the room the limit holds to`() {
        val outcome = vaultOutcome(vault(periodic = false, preventive = false), captured())

        val keeps = assertIs<VaultOutcome.Keeps>(outcome)
        assertEquals(10, keeps.copies, "the limit is still what a capture would sweep to")
        assertEquals(fourMegabytes, keeps.eachBytes)
        assertNull(keeps.historyDays)
    }

    // ------------------------------------------------------- keeping everything, in amber

    @Test
    fun `keeping everything is said with the periodic trigger off`() {
        val outcome = vaultOutcome(
            vault(periodic = false, retention = BackupRetention.EVERYTHING),
            captured(),
        )

        val all = assertIs<VaultOutcome.KeepsEverything>(
            outcome,
            "the amber belongs to the choice, not to a trigger",
        )
        assertNull(all.perMonthBytes, "a rate per month out of a wait nothing consults has no source")
    }

    @Test
    fun `keeping everything states the rate while something goes by the clock`() {
        val outcome = vaultOutcome(
            vault(periodic = true, retention = BackupRetention.EVERYTHING),
            captured(),
        )

        val all = assertIs<VaultOutcome.KeepsEverything>(outcome)
        assertEquals(
            fourMegabytes * 10,
            all.perMonthBytes,
            "ten waits of three days fit in a month, at four megabytes each",
        )
    }

    // ------------------------------------------------------------- nothing measured yet

    @Test
    fun `a vault that has never captured invents no size`() {
        val outcome = vaultOutcome(vault(), VaultCopies())

        val keeps = assertIs<VaultOutcome.Keeps>(outcome)
        assertNull(keeps.eachBytes, "zero bytes a copy is a measurement, and none has been taken")
        assertEquals(30, keeps.historyDays, "how far back it will reach is known before it does")
    }

    @Test
    fun `a vault that has never captured claims no rate either`() {
        val outcome = vaultOutcome(
            vault(retention = BackupRetention.EVERYTHING),
            VaultCopies(),
        )

        val all = assertIs<VaultOutcome.KeepsEverything>(outcome)
        assertNull(all.perMonthBytes, "nothing times ten waits is still nothing measured")
    }

    // ------------------------------------------------------------------------ the waits

    @Test
    fun `the span is the wait times the limit`() {
        val fifteen = vaultOutcome(
            vault(interval = VaultInterval.FIFTEEN_DAYS, retention = BackupRetention.FIVE),
            captured(),
        )

        assertEquals(75, assertIs<VaultOutcome.Keeps>(fifteen).historyDays)
    }

    @Test
    fun `a wait that fills the month piles up one copy of it`() {
        val outcome = vaultOutcome(
            vault(interval = VaultInterval.FIFTEEN_DAYS, retention = BackupRetention.EVERYTHING),
            captured(),
        )

        assertEquals(
            fourMegabytes * 2,
            assertIs<VaultOutcome.KeepsEverything>(outcome).perMonthBytes,
            "two fifteen-day waits fit in a month",
        )
    }
}
