package com.neoutils.finance.extension

import kotlinx.datetime.YearMonth

val yearMonthFormat = YearMonth.Format {
    monthName(MonthNamesPortuguese)
    chars(", ")
    year()
}