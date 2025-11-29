@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.extension

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import kotlin.time.ExperimentalTime

val LocalDateTime.yearMonth: YearMonth
    get() = YearMonth(year, month)

