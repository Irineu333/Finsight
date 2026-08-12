package com.neoutils.finsight.ui.modal.createInvoice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.create_invoice_confirm
import com.neoutils.finsight.resources.create_invoice_message
import com.neoutils.finsight.resources.create_invoice_month_label
import com.neoutils.finsight.resources.create_invoice_month_taken
import com.neoutils.finsight.resources.create_invoice_title
import com.neoutils.finsight.resources.create_invoice_window
import com.neoutils.finsight.ui.component.InvoiceMonthNavigator
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.util.LocalDateFormats
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Creates the invoice of any due month the card still lacks — past or future.
 *
 * The month is navigated and never picked from a list, because the interesting month is
 * precisely the one that has no invoice yet. An occupied month stays visible and says so:
 * skipping it would take from the user the sense of where they are in the calendar.
 */
class CreateInvoiceModal(
    private val creditCard: CreditCard,
    private val initialDueMonth: YearMonth,
    private val onCreated: (Invoice) -> Unit,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<CreateInvoiceViewModel> {
            parametersOf(creditCard, initialDueMonth, onCreated)
        }

        val uiState by viewModel.uiState.collectAsState()
        val formats = LocalDateFormats.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.create_invoice_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.create_invoice_message),
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            InvoiceMonthNavigator(
                selection = uiState.selection,
                onNavigate = { month ->
                    viewModel.onAction(CreateInvoiceAction.SelectDueMonth(month))
                },
                label = stringResource(Res.string.create_invoice_month_label),
                modifier = Modifier.testTag("create_invoice_month"),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // The window promised here is the one that gets written: both are
            // `InvoiceMonthSelection.window`, derived from the card.
            Text(
                text = if (uiState.selection.isNew) {
                    stringResource(
                        Res.string.create_invoice_window,
                        formats.monthDayYear.format(uiState.window.openingDate),
                        formats.monthDayYear.format(uiState.window.closingDate),
                    )
                } else {
                    stringResource(Res.string.create_invoice_month_taken)
                },
                fontSize = 12.sp,
                color = if (uiState.selection.isNew) {
                    colorScheme.onSurfaceVariant
                } else {
                    colorScheme.error
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.onAction(CreateInvoiceAction.Submit) },
                enabled = uiState.canSubmit,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_invoice_confirm"),
            ) {
                Text(
                    text = stringResource(Res.string.create_invoice_confirm),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
