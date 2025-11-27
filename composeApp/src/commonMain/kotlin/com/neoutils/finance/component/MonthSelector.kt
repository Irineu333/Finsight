package com.neoutils.finance.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.neoutils.finance.screen.dashboard.YearMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding

private val MonthNamesPortuguese = MonthNames(
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
)

private val monthFormat = LocalDate.Format {
    monthName(MonthNamesPortuguese)
    chars(" ")
    year()
}

@Composable
fun MonthSelector(
    selectedYearMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) = Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
) {
    IconButton(onClick = onPreviousMonth) {
        Icon(
            imageVector = Icons.Default.ChevronLeft,
            contentDescription = null,
        )
    }

    Text(
        text = monthFormat.format(selectedYearMonth.date),
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
    )

    IconButton(onClick = onNextMonth) {
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
        )
    }
}
