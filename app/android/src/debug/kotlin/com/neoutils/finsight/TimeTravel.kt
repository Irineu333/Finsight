package com.neoutils.finsight

import android.app.Activity
import android.util.Log
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.core.context.GlobalContext
import kotlin.time.Clock
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
    val offsetDays = longArgument(CLOCK_OFFSET_DAYS)
    val offsetMonths = longArgument(CLOCK_OFFSET_MONTHS)

    val monthsInDays = calendarMonthsInDays(offsetMonths)

    GlobalContext.get().get<ShiftableClock>().shiftTo((offsetDays + monthsInDays).days)

    // Logged because the failure this guards against is silent: a shift that did not take looks
    // exactly like a screen that refuses to offer what the flow is about to tap.
    Log.i(TIME_TRAVEL_TAG, "clock offset: $offsetDays day(s) + $offsetMonths month(s) = ${offsetDays + monthsInDays} day(s)")
}

/**
 * How many days away the same day-of-month is, [months] from now.
 *
 * The clock only understands a [kotlin.time.Duration], and a month is not one: adding 31 days to
 * the 30th of a 31-day month lands two months on, which would make a flow red on a handful of days
 * a year for a reason nothing on screen names. Asking the calendar and converting the answer to
 * days keeps "next month" meaning next month on every date the suite can run on.
 *
 * A month shorter than the current day-of-month clamps — 31 January plus one month is 28 February —
 * which is the same rule the invoice periods already follow.
 */
private fun calendarMonthsInDays(months: Long): Long {
    if (months == 0L) return 0L

    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return today.daysUntil(today.plus(months.toInt(), DateTimeUnit.MONTH)).toLong()
}

/**
 * Reads whatever type the argument arrived as. Maestro types a YAML scalar for us — `45` comes
 * through as a number, `"45"` as a string — and which of the two a flow wrote is not a distinction
 * worth failing silently over.
 */
@Suppress("DEPRECATION")
private fun Activity.longArgument(name: String): Long =
    when (val value = intent?.extras?.get(name)) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    } ?: 0L

private const val CLOCK_OFFSET_DAYS = "clockOffsetDays"
private const val CLOCK_OFFSET_MONTHS = "clockOffsetMonths"
private const val TIME_TRAVEL_TAG = "TimeTravel"
