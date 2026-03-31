package com.neoutils.finsight.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.day_of_week_friday
import com.neoutils.finsight.resources.day_of_week_monday
import com.neoutils.finsight.resources.day_of_week_saturday
import com.neoutils.finsight.resources.day_of_week_sunday
import com.neoutils.finsight.resources.day_of_week_thursday
import com.neoutils.finsight.resources.day_of_week_tuesday
import com.neoutils.finsight.resources.day_of_week_wednesday
import com.neoutils.finsight.resources.month_april
import com.neoutils.finsight.resources.month_august
import com.neoutils.finsight.resources.month_december
import com.neoutils.finsight.resources.month_february
import com.neoutils.finsight.resources.month_january
import com.neoutils.finsight.resources.month_july
import com.neoutils.finsight.resources.month_june
import com.neoutils.finsight.resources.month_march
import com.neoutils.finsight.resources.month_may
import com.neoutils.finsight.resources.month_november
import com.neoutils.finsight.resources.month_october
import com.neoutils.finsight.resources.month_september
import com.neoutils.finsight.util.DateFormats
import com.neoutils.finsight.util.LocalDateFormats
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun FormattingLocalsHost(
    content: @Composable () -> Unit,
) {
    val formatter = koinInject<CurrencyFormatter>()
    val dateFormats = DateFormats(
        monthNames = MonthNames(
            stringResource(Res.string.month_january),
            stringResource(Res.string.month_february),
            stringResource(Res.string.month_march),
            stringResource(Res.string.month_april),
            stringResource(Res.string.month_may),
            stringResource(Res.string.month_june),
            stringResource(Res.string.month_july),
            stringResource(Res.string.month_august),
            stringResource(Res.string.month_september),
            stringResource(Res.string.month_october),
            stringResource(Res.string.month_november),
            stringResource(Res.string.month_december),
        ),
        dayOfWeekNames = DayOfWeekNames(
            stringResource(Res.string.day_of_week_monday),
            stringResource(Res.string.day_of_week_tuesday),
            stringResource(Res.string.day_of_week_wednesday),
            stringResource(Res.string.day_of_week_thursday),
            stringResource(Res.string.day_of_week_friday),
            stringResource(Res.string.day_of_week_saturday),
            stringResource(Res.string.day_of_week_sunday),
        ),
    )

    CompositionLocalProvider(
        LocalCurrencyFormatter provides formatter,
        LocalDateFormats provides dateFormats,
        content = content,
    )
}
