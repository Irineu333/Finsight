package com.neoutils.finsight.domain.vault

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_interval_1
import com.neoutils.finsight.resources.backup_interval_15
import com.neoutils.finsight.resources.backup_interval_3
import com.neoutils.finsight.resources.backup_interval_7
import com.neoutils.finsight.util.UiText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * The four waits the settings sheet offers between one look for a reason to copy and the
 * next.
 *
 * The vault itself keeps a [Duration], because nothing it does needs the value to be one of
 * four — the question it asks is whether that much time has gone by. This is the offer, and
 * it exists so that the sheet has four short mutually exclusive values to lay out and one
 * label each, rather than a number somebody types.
 *
 * [DEFAULT_INTERVAL] is [THREE_DAYS]: high frequency with a short history, because
 * restoring is all-or-nothing and a three-week-old copy costs three weeks of entries
 * (design D5).
 */
enum class VaultInterval(val duration: Duration) {
    ONE_DAY(1.days),
    THREE_DAYS(3.days),
    SEVEN_DAYS(7.days),
    FIFTEEN_DAYS(15.days);

    companion object {

        /**
         * The offered value [duration] is, or the nearest one when it is none of them.
         *
         * A stored preference outlives the build that wrote it, so a value that was offered
         * once and is not any more still has to be rendered as something. The nearest is
         * the honest choice: it never claims a wait shorter than the one in force by more
         * than the gap between two offers, and the vault goes on using the stored value
         * until somebody picks from the sheet.
         */
        fun nearest(duration: Duration): VaultInterval =
            entries.minBy { (it.duration - duration).absoluteValue }
    }
}

/**
 * How long the vault waits, as a wait a person reads.
 *
 * A [UiText] rather than a rendered string because the sentence it goes into is written in
 * two places that resolve text differently — the settings sheet, in a composition, and the
 * offer's terms, which are built by a view model before there is one.
 */
val VaultInterval.label: UiText
    get() = UiText.Res(
        when (this) {
            VaultInterval.ONE_DAY -> Res.string.backup_interval_1
            VaultInterval.THREE_DAYS -> Res.string.backup_interval_3
            VaultInterval.SEVEN_DAYS -> Res.string.backup_interval_7
            VaultInterval.FIFTEEN_DAYS -> Res.string.backup_interval_15
        }
    )
