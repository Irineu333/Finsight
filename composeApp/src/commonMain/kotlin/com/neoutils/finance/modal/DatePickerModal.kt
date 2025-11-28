@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)

package com.neoutils.finance.modal

import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.neoutils.finance.manager.LocalModalManager
import com.neoutils.finance.manager.Modal
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DatePickerModal(
    private val initialDate: LocalDate,
    private val onDateSelected: (LocalDate) -> Unit,
) : Modal {

    @Composable
    override fun Content() {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDateToMillis(initialDate)
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