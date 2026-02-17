@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
fun MonthPickerDropdownMenu(
    expanded: Boolean,
    selectedYearMonth: YearMonth,
    onDismissRequest: () -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
    menuWidth: Dp = 320.dp,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    modifier: Modifier = Modifier
) {
    var selectedYear by remember { mutableIntStateOf(selectedYearMonth.year) }

    LaunchedEffect(expanded, selectedYearMonth) {
        if (expanded) {
            selectedYear = selectedYearMonth.year
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(12.dp),
        containerColor = MenuDefaults.containerColor,
        offset = offset,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .width(menuWidth)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            YearSelector(
                year = selectedYear,
                onPreviousYear = { selectedYear-- },
                onNextYear = { selectedYear++ },
                modifier = Modifier.fillMaxWidth()
            )

            MonthGrid(
                selectedYear = selectedYear,
                selectedMonth = selectedYearMonth.month,
                onMonthSelected = { month ->
                    onMonthSelected(YearMonth(selectedYear, month))
                    onDismissRequest()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun YearSelector(
    year: Int,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousYear) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = colorScheme.onSurface
                )
            }

            Text(
                text = year.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )

            IconButton(onClick = onNextYear) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    selectedYear: Int,
    selectedMonth: Month,
    onMonthSelected: (Month) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentYearMonth = remember {
        Clock.System.todayIn(TimeZone.currentSystemDefault()).yearMonth
    }

    val months = listOf(
        Month.JANUARY to "JAN",
        Month.FEBRUARY to "FEV",
        Month.MARCH to "MAR",
        Month.APRIL to "ABR",
        Month.MAY to "MAI",
        Month.JUNE to "JUN",
        Month.JULY to "JUL",
        Month.AUGUST to "AGO",
        Month.SEPTEMBER to "SET",
        Month.OCTOBER to "OUT",
        Month.NOVEMBER to "NOV",
        Month.DECEMBER to "DEZ"
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        months.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (month, label) ->
                    val isCurrentMonth = selectedYear == currentYearMonth.year &&
                        month == currentYearMonth.month

                    MonthChip(
                        label = label,
                        isSelected = month == selectedMonth,
                        isCurrentMonth = isCurrentMonth,
                        onClick = { onMonthSelected(month) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthChip(
    label: String,
    isSelected: Boolean,
    isCurrentMonth: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) colorScheme.primary else Color.Transparent
    val contentColor = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface
    val borderColor = when {
        isSelected -> colorScheme.primary
        isCurrentMonth -> colorScheme.primary
        else -> colorScheme.outline.copy(alpha = 0.3f)
    }

    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}
