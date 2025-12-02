package com.neoutils.finance.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
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
import com.neoutils.finance.extension.yearMonthFormat
import kotlinx.datetime.YearMonth

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
        text = yearMonthFormat.format(selectedYearMonth),
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
