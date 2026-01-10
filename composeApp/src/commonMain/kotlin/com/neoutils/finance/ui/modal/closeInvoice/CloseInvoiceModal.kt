package com.neoutils.finance.ui.modal.closeInvoice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.util.DateFormats
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class CloseInvoiceModal(
    private val invoiceId: Long,
    private val closingDate: LocalDate,
) : ModalBottomSheet() {

    private val formats = DateFormats()

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<CloseInvoiceViewModel> { parametersOf(invoiceId) }
        val date = rememberTextFieldState(formats.dayMonthYear.format(closingDate))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Fechar Fatura",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Após o fechamento, a fatura não poderá receber novos gastos.",
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                state = date,
                label = { Text(text = "Data de Fechamento") },
                enabled = false,
                readOnly = true,
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.closeInvoice(closingDate) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Fechar Fatura",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
