@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.report.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.report_config_cancel
import com.neoutils.finsight.resources.report_config_confirm
import com.neoutils.finsight.resources.report_config_end_date
import com.neoutils.finsight.resources.report_config_start_date
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

@Composable
fun DateRangeCard(
    startDate: LocalDate?,
    endDate: LocalDate?,
    onSelectStartDate: (LocalDate) -> Unit,
    onSelectEndDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {},
            dismissButton = {},
        ) {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = startDate?.toEpochDays()?.times(86400000L),
            )
            DatePicker(state = state)
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text(stringResource(Res.string.report_config_cancel))
                }
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            onSelectStartDate(LocalDate.fromEpochDays((millis / 86400000L).toInt()))
                        }
                        showStartDatePicker = false
                    }
                ) {
                    Text(stringResource(Res.string.report_config_confirm))
                }
            }
        }
    }

    if (showEndDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {},
            dismissButton = {},
        ) {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = endDate?.toEpochDays()?.times(86400000L),
            )
            DatePicker(state = state)
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text(stringResource(Res.string.report_config_cancel))
                }
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            onSelectEndDate(LocalDate.fromEpochDays((millis / 86400000L).toInt()))
                        }
                        showEndDatePicker = false
                    }
                ) {
                    Text(stringResource(Res.string.report_config_confirm))
                }
            }
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showStartDatePicker = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = colorScheme.primary,
            )
            Column {
                Text(
                    text = stringResource(Res.string.report_config_start_date),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                )
                Text(
                    text = startDate?.toString() ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showEndDatePicker = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = colorScheme.primary,
            )
            Column {
                Text(
                    text = stringResource(Res.string.report_config_end_date),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                )
                Text(
                    text = endDate?.toString() ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
