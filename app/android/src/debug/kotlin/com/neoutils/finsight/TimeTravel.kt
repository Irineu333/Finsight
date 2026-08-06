package com.neoutils.finsight

import android.app.Activity
import com.neoutils.finsight.util.ShiftableClock
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.days

/**
 * Moves the app's clock forward by the number of days a launch argument names, so an E2E flow can
 * reach behaviour that only exists once time has passed — an invoice past its closing date, above
 * all — without touching the device's own clock, which a production build does not allow anyway.
 *
 * Maestro passes launch arguments as intent extras:
 *
 * ```yaml
 * - launchApp:
 *     clearState: false      # the shift has to land on state the flow already created
 *     arguments:
 *       clockOffsetDays: 45
 * ```
 *
 * `clearState: false` is the point: the card and its invoice were created before the jump, and it
 * is precisely those rows that have to age. A relaunch is what makes the shift take: the process
 * is new, so every ViewModel and mapper reads the moved clock from its first composition, rather
 * than holding figures derived from a clock that changed underneath them.
 *
 * This file exists only in the debug source set — the release APK does not contain it, so there is
 * nothing to strip and nothing to accidentally leave reachable.
 */
fun Activity.applyTimeTravel() {
    // Read whatever type the argument arrived as. Maestro types a YAML scalar for us — `45` comes
    // through as a number, `"45"` as a string — and which of the two a flow wrote is not a
    // distinction worth failing silently over.
    @Suppress("DEPRECATION")
    val offsetDays = when (val value = intent?.extras?.get(CLOCK_OFFSET_DAYS)) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    } ?: return

    GlobalContext.get().get<ShiftableClock>().shiftBy(offsetDays.days)
}

private const val CLOCK_OFFSET_DAYS = "clockOffsetDays"
