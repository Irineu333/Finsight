package com.neoutils.finance.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.util.DateFormats
import kotlinx.datetime.YearMonth

private val formats = DateFormats()

@Composable
fun MonthSelector(
    selectedYearMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onMonthSelected: ((YearMonth) -> Unit)? = null,
    showPickerChevron: Boolean = true,
    modifier: Modifier = Modifier
) = Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
) {
    var isMonthPickerExpanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableIntStateOf(0) }
    val menuWidth = 320.dp
    val menuOffsetX = with(LocalDensity.current) {
        (anchorWidthPx.toDp() - menuWidth) / 2
    }

    IconButton(onClick = onPreviousMonth) {
        Icon(
            imageVector = Icons.Default.ChevronLeft,
            contentDescription = null,
        )
    }

    Box {
        Row(
            modifier = Modifier.onSizeChanged { anchorWidthPx = it.width },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formats.yearMonth.format(selectedYearMonth),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = if (onMonthSelected != null) {
                    Modifier.clickable { isMonthPickerExpanded = true }
                } else {
                    Modifier
                }
            )

            if (onMonthSelected != null && showPickerChevron) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .clickable { isMonthPickerExpanded = true }
                        .padding(top = 1.dp)
                )
            }
        }

        if (onMonthSelected != null) {
            MonthPickerDropdownMenu(
                expanded = isMonthPickerExpanded,
                selectedYearMonth = selectedYearMonth,
                onDismissRequest = { isMonthPickerExpanded = false },
                onMonthSelected = onMonthSelected,
                menuWidth = menuWidth,
                offset = DpOffset(x = menuOffsetX, y = 4.dp)
            )
        }
    }

    IconButton(onClick = onNextMonth) {
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
        )
    }
}
