package com.neoutils.finsight.ui.modal.vaultSettings

import com.neoutils.finsight.domain.vault.VaultInterval
import com.neoutils.finsight.domain.vault.VaultState
import com.neoutils.finsight.domain.vault.copiesKept
import com.neoutils.finsight.ui.screen.backup.DAYS_PER_MONTH
import com.neoutils.finsight.ui.screen.backup.VaultCopies

/**
 * What the settings in force produce, worked out from the vault and from the copies already
 * written.
 *
 * It is a value rather than a branch inside the sheet because each of its readings has a
 * different source, and only some of them have one at a time: the room a limit takes is the
 * limit and a measured average, while a span of history needs a wait as well — and a wait is
 * in force only while the trigger that consults it is (`VaultPeriodicBackup`). Stated here,
 * the four combinations of the two triggers can be put to it directly.
 *
 * **A reading with no source is null and not zero.** A vault that has never captured knows
 * no average, and a vault whose periodic trigger is off has no rate per month to state —
 * neither is "0 MB", and printing one would be inventing a measurement.
 */
internal sealed interface VaultOutcome {

    /**
     * Nothing is ever removed, whichever trigger writes the copy.
     *
     * @property perMonthBytes how fast the copies pile up, or null when nothing has been
     * captured yet or no trigger goes by the clock — a rate stated per month out of a wait
     * nothing consults would be a number with no source.
     */
    data class KeepsEverything(val perMonthBytes: Long?) : VaultOutcome

    /**
     * A limit is in force, and the sweep behind every capture enforces it.
     *
     * @property copies how many survive a sweep.
     * @property historyDays how far back those copies reach, or null when no trigger goes by
     * the clock — copies taken before deletions arrive at the rate somebody deletes things,
     * which the app does not know.
     * @property eachBytes what one copy weighs on average, or null while none has been
     * written.
     */
    data class Keeps(
        val copies: Int,
        val historyDays: Int?,
        val eachBytes: Long?,
    ) : VaultOutcome
}

/** The reading of [vault] against the [copies] that are actually in the destination. */
internal fun vaultOutcome(vault: VaultState, copies: VaultCopies): VaultOutcome {
    val each = if (copies.count > 0) copies.totalBytes / copies.count else null
    val wait = vault.isPeriodicOn
        .takeIf { it }
        ?.let { VaultInterval.nearest(vault.interval).duration.inWholeDays.coerceAtLeast(1) }

    val kept = vault.copiesKept() ?: return VaultOutcome.KeepsEverything(
        perMonthBytes = if (each != null && wait != null) each * (DAYS_PER_MONTH / wait) else null,
    )

    return VaultOutcome.Keeps(
        copies = kept,
        historyDays = wait?.let { (it * kept).toInt() },
        eachBytes = each,
    )
}
