@file:OptIn(FormatStringsInDatetimeFormats::class)

package com.neoutils.finsight.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern

val dayMonthYear = LocalDate.Format {
    byUnicodePattern("dd/MM/yyyy")
}

val dayMonth = LocalDate.Format {
    byUnicodePattern("dd/MM")
}
