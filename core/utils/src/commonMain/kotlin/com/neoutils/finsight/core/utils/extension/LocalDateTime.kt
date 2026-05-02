@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.core.utils.extension

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import kotlin.time.ExperimentalTime

val LocalDateTime.yearMonth get() = date.yearMonth
