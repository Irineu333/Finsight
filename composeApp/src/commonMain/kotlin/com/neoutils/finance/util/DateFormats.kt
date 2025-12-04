@file:OptIn(FormatStringsInDatetimeFormats::class)

package com.neoutils.finance.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.byUnicodePattern

class DateFormats(
    private val monthNames: MonthNames = MonthNames(
        january = "Janeiro",
        february = "Fevereiro",
        march = "Março",
        april = "Abril",
        may = "Maio",
        june = "Junho",
        july = "Julho",
        august = "Agosto",
        september = "Setembro",
        october = "Outubro",
        november = "Novembro",
        december = "Dezembro"
    ),
    private val dayOfWeekNames: DayOfWeekNames = DayOfWeekNames(
        sunday = "Domingo",
        monday = "Segunda-feira",
        tuesday = "Terça-feira",
        wednesday = "Quarta-feira",
        thursday = "Quinta-feira",
        friday = "Sexta-feira",
        saturday = "Sábado"
    )
) {
    val dayOfWeek = LocalDate.Format {
        day()
        chars(", ")
        dayOfWeek(dayOfWeekNames)
    }

    val yearMonth = YearMonth.Format {
        monthName(monthNames)
        chars(", ")
        year()
    }

    val dayMonthYear = LocalDate.Format {
        byUnicodePattern("dd/MM/yyyy")
    }
}