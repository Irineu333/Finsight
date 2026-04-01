@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.date

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.date_picker_cancel
import com.neoutils.finsight.resources.date_picker_confirm
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
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
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
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

        DateRangePicker(
            state = state,
            headline = null,
            title = null,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            modifier = Modifier.fillMaxHeight(fraction = 0.6f),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = { manager.dismiss() }) {
                Text(stringResource(Res.string.date_picker_cancel))
            }

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
