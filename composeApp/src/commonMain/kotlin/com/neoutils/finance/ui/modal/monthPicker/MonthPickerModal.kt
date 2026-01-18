package com.neoutils.finance.ui.modal.monthPicker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.ModalBottomSheet
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth

class MonthPickerModal(
    private val initialYearMonth: YearMonth,
    private val onMonthSelected: (YearMonth) -> Unit
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val modalManager = LocalModalManager.current

        var selectedYear by remember { mutableStateOf(initialYearMonth.year) }
        var selectedMonth by remember { mutableStateOf(initialYearMonth.month) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Período",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(24.dp))

            YearSelector(
                year = selectedYear,
                onPreviousYear = { selectedYear-- },
                onNextYear = { selectedYear++ },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            MonthGrid(
                selectedMonth = selectedMonth,
                onMonthSelected = { selectedMonth = it },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    onMonthSelected(YearMonth(selectedYear, selectedMonth))
                    modalManager.dismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary
                )
            ) {
                Text(
                    text = "Confirmar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
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
            colors = CardDefaults.outlinedCardColors(
                containerColor = Color.Transparent
            ),
            border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
        selectedMonth: Month,
        onMonthSelected: (Month) -> Unit,
        modifier: Modifier = Modifier
    ) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            months.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { (month, label) ->
                        MonthChip(
                            label = label,
                            isSelected = month == selectedMonth,
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
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val containerColor = if (isSelected) {
            colorScheme.primary
        } else {
            Color.Transparent
        }

        val contentColor = if (isSelected) {
            colorScheme.onPrimary
        } else {
            colorScheme.onSurface
        }

        val borderColor = if (isSelected) {
            colorScheme.primary
        } else {
            colorScheme.outline.copy(alpha = 0.3f)
        }

        OutlinedCard(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = containerColor
            ),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
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
}
