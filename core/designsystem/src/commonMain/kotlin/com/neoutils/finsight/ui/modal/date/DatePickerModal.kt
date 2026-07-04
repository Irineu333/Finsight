@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.date

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DatePickerModal(
    private val initialDate: LocalDate? = null,
    private val onDateSelected: (LocalDate) -> Unit,
    private val minDate: LocalDate? = null,
    private val maxDate: LocalDate? = null,
) : ModalBottomSheet() {

    val selectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
            val date = millisToLocalDate(utcTimeMillis)
            val afterMin = minDate?.let { date >= it } ?: true
            val beforeMax = maxDate?.let { date <= it } ?: true
            return afterMin && beforeMax
        }
    }

    @Composable
    override fun ColumnScope.BottomSheetContent() {

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDateToMillis(
                initialDate ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            ),
            selectableDates = selectableDates
        )

        val confirmEnabled by remember {
            derivedStateOf { datePickerState.selectedDateMillis != null }
        }

        val manager = LocalModalManager.current

        DatePicker(
            state = datePickerState,
            headline = null,
            title = null,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(
                onClick = { manager.dismiss() }
            ) {
                Text(stringResource(Res.string.date_picker_cancel))
            }

            Button(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDateSelected(millisToLocalDate(millis))
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
