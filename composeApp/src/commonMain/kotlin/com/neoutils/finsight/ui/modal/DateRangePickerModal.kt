@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.modal

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.date_picker_cancel
import com.neoutils.finsight.resources.date_picker_confirm
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.Modal
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DateRangePickerModal(
    private val initialStartDate: LocalDate? = null,
    private val initialEndDate: LocalDate? = null,
    private val onRangeSelected: (start: LocalDate, end: LocalDate) -> Unit,
) : Modal() {

    @Composable
    override fun Content() {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = initialStartDate?.let { localDateToMillis(it) },
            initialSelectedEndDateMillis = initialEndDate?.let { localDateToMillis(it) },
        )

        val confirmEnabled by remember {
            derivedStateOf {
                state.selectedStartDateMillis != null && state.selectedEndDateMillis != null
            }
        }

        val manager = LocalModalManager.current

        DatePickerDialog(
            onDismissRequest = { manager.dismiss() },
            confirmButton = {
                Button(
                    onClick = {
                        val start = state.selectedStartDateMillis?.let { millisToLocalDate(it) }
                        val end = state.selectedEndDateMillis?.let { millisToLocalDate(it) }
                        if (start != null && end != null) {
                            onRangeSelected(start, end)
                        }
                        manager.dismiss()
                    },
                    enabled = confirmEnabled,
                ) {
                    Text(stringResource(Res.string.date_picker_confirm))
                }
            },
            dismissButton = {
                Button(onClick = { manager.dismiss() }) {
                    Text(stringResource(Res.string.date_picker_cancel))
                }
            },
        ) {
            DateRangePicker(
                state = state,
                modifier = Modifier.weight(1f),
            )
        }
    }

    private fun localDateToMillis(date: LocalDate): Long {
        return date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    }

    private fun millisToLocalDate(millis: Long): LocalDate {
        return Instant.fromEpochMilliseconds(millis)
            .toLocalDateTime(TimeZone.UTC)
            .date
    }
}
