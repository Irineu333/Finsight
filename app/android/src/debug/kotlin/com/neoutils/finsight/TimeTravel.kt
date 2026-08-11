package com.neoutils.finsight

import android.app.Activity
import com.neoutils.finsight.debug.applyTimeTravel

/**
 * Reads the clock offset the way Android delivers it, and hands it to the shared hook.
 *
 * Maestro passes launch arguments as intent extras here, and as process arguments on iOS; what the
 * offset then *means* — the calendar-month conversion, the shift, the log — is one implementation
 * in `:app:debug`, so the two platforms cannot drift apart on it.
 *
 * This file exists only in the debug source set — the release APK does not contain it, so there is
 * nothing to strip and nothing to accidentally leave reachable.
 */
fun Activity.applyTimeTravel() = applyTimeTravel(
    offsetDays = longArgument(CLOCK_OFFSET_DAYS),
    offsetMonths = longArgument(CLOCK_OFFSET_MONTHS),
)

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
