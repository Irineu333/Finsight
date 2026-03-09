@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.report.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.DateRangePickerModal
import kotlinx.datetime.*
import kotlinx.datetime.format.char
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

private data class QuickPeriod(
    val labelRes: StringResource,
    val start: LocalDate,
    val end: LocalDate,
)

@Composable
fun DateRangeCard(
    startDate: LocalDate?,
    endDate: LocalDate?,
    onRangeSelected: (start: LocalDate, end: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    val quickPeriods = remember(today) {
        val firstThisMonth = LocalDate(today.year, today.month, 1)
        val lastThisMonth = firstThisMonth.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        val firstLastMonth = firstThisMonth.minus(1, DateTimeUnit.MONTH)
        val lastLastMonth = firstThisMonth.minus(1, DateTimeUnit.DAY)

        listOf(
            QuickPeriod(Res.string.report_config_period_last_7_days, today.minus(6, DateTimeUnit.DAY), today),
            QuickPeriod(Res.string.report_config_period_last_30_days, today.minus(29, DateTimeUnit.DAY), today),
            QuickPeriod(Res.string.report_config_period_this_month, firstThisMonth, lastThisMonth),
            QuickPeriod(Res.string.report_config_period_last_month, firstLastMonth, lastLastMonth),
            QuickPeriod(Res.string.report_config_period_this_year, LocalDate(today.year, 1, 1), LocalDate(today.year, 12, 31)),
        )
    }

    val modalManager = LocalModalManager.current
    val colorScheme = MaterialTheme.colorScheme

    val dateFormat = remember {
        LocalDate.Format {
            dayOfMonth()
            char('/')
            monthNumber()
            char('/')
            year()
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
            shape = RoundedCornerShape(16.dp),
            onClick = {
                modalManager.show(
                    DateRangePickerModal(
                        initialStartDate = startDate,
                        initialEndDate = endDate,
                        onRangeSelected = onRangeSelected,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = colorScheme.primary,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.report_config_date_range),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    val hasRange = startDate != null && endDate != null
                    Text(
                        text = if (hasRange) {
                            "${dateFormat.format(startDate!!)} – ${dateFormat.format(endDate!!)}"
                        } else {
                            stringResource(Res.string.report_config_period_select)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (hasRange) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(quickPeriods) { period ->
                val selected = period.start == startDate && period.end == endDate
                FilterChip(
                    selected = selected,
                    onClick = { onRangeSelected(period.start, period.end) },
                    label = { Text(stringResource(period.labelRes)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.primaryContainer,
                        selectedLabelColor = colorScheme.onPrimaryContainer,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = colorScheme.outline,
                        selectedBorderColor = colorScheme.primaryContainer,
                    ),
                )
            }
        }
    }
}