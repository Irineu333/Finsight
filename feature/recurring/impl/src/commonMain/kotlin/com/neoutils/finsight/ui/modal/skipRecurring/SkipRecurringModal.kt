package com.neoutils.finsight.ui.modal.skipRecurring

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.skip_recurring_confirm
import com.neoutils.finsight.resources.skip_recurring_message
import com.neoutils.finsight.resources.skip_recurring_title
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.theme.Warning
import com.neoutils.finsight.util.LocalDateFormats
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Asks before a cycle is skipped.
 *
 * Skipping writes an occurrence and takes the cycle off the pending list, which is the
 * kind of thing the app confirms first — archiving and deleting a recurring both do.
 * It sat one tap away from the amount field, with no question asked.
 */
class SkipRecurringModal(
    private val recurring: Recurring,
    private val date: LocalDate,
    private val target: TransactionTarget,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<SkipRecurringViewModel> {
            parametersOf(recurring, date, target)
        }
        // The cycle is named by its month, in the user's own month names — a skip is a
        // decision about a month, and a bare date would leave the user to work that out.
        val cycleMonth = LocalDateFormats.current.yearMonth.format(date.yearMonth)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(Res.string.skip_recurring_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(
                    Res.string.skip_recurring_message,
                    recurring.label,
                    cycleMonth,
                ),
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.skip() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("skip_recurring_confirm"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Warning,
                ),
            ) {
                Text(
                    text = stringResource(Res.string.skip_recurring_confirm),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
