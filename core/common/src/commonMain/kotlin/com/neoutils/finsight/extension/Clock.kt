@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.extension

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Today in the device's zone, read from the clock the caller was given. */
fun Clock.today(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
    now().toLocalDateTime(timeZone).date

/** The month today falls in, read from the clock the caller was given. */
fun Clock.currentYearMonth(): YearMonth = now().toYearMonth()
