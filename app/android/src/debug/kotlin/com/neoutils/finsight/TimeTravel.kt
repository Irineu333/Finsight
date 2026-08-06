package com.neoutils.finsight

import android.app.Activity
import android.util.Log
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
 * An absent argument means the present, not "leave it as it was": a relaunch that carries no
 * offset resets the clock. Otherwise a flow that jumps once would keep the shift for the rest of
 * a surviving process, silently, and nothing on screen would say so.
 *
 * What moves the clock is this call; what makes a *screen* show the move is the new composition a
 * relaunch brings with it. A launch that leaves the process alive (`stopApp: false`) still updates
 * the clock — so whatever is written or read from here on is consistent — but figures already on
 * screen were derived before it and do not recompute. Flows should jump with a full relaunch,
 * which is Maestro's default.
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
    } ?: 0L

    GlobalContext.get().get<ShiftableClock>().shiftTo(offsetDays.days)

    // Logged because the failure this guards against is silent: a shift that did not take looks
    // exactly like a screen that refuses to offer what the flow is about to tap.
    Log.i(TIME_TRAVEL_TAG, "clock offset: $offsetDays day(s)")
}

private const val CLOCK_OFFSET_DAYS = "clockOffsetDays"
private const val TIME_TRAVEL_TAG = "TimeTravel"
