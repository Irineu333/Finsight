@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)

package com.neoutils.finance.ui.modal

import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.Modal
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DatePickerModal(
    private val initialDate: LocalDate,
    private val onDateSelected: (LocalDate) -> Unit,
    private val minDate: LocalDate? = null,
    private val maxDate: LocalDate? = null,
) : Modal() {

    @Composable
    override fun Content() {
        val selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = millisToLocalDate(utcTimeMillis)
                val afterMin = minDate?.let { date >= it } ?: true
                val beforeMax = maxDate?.let { date <= it } ?: true
                return afterMin && beforeMax
            }
        }

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDateToMillis(initialDate),
            selectableDates = selectableDates
        )

        val confirmEnabled by remember {
            derivedStateOf { datePickerState.selectedDateMillis != null }
        }

        val manager = LocalModalManager.current

        DatePickerDialog(
            onDismissRequest = {
                manager.dismiss()
            },
            confirmButton = {
                Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onDateSelected(millisToLocalDate(millis))
                        }
                        manager.dismiss()
                    },
                    enabled = confirmEnabled
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        manager.dismiss()
                    }
                ) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
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