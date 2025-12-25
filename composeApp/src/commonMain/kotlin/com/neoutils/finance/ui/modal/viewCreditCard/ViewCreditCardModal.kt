@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.viewCreditCard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.extension.toYearMonth
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.ui.modal.advancePayment.AdvancePaymentModal
import com.neoutils.finance.ui.modal.closeInvoice.CloseInvoiceModal
import com.neoutils.finance.ui.modal.deleteCreditCard.DeleteCreditCardModal
import com.neoutils.finance.ui.modal.editCreditCard.EditCreditCardModal
import com.neoutils.finance.ui.modal.openInvoice.OpenInvoiceModal
import com.neoutils.finance.ui.modal.payInvoice.PayInvoiceModal
import com.neoutils.finance.ui.modal.reopenInvoice.ReopenInvoiceModal
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import com.neoutils.finance.ui.theme.Info
import com.neoutils.finance.util.DateFormats
import kotlinx.datetime.onDay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ViewCreditCardModal(
    private val creditCard: CreditCard,
) :
    ModalBottomSheet() {

    private val formats = DateFormats()

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val modalManager = LocalModalManager.current

        val viewModel = koinViewModel<ViewCreditCardViewModel>(key = key) {
            parametersOf(creditCard)
        }

        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Text(
                text = uiState.creditCard.name,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )

            Text(
                text = "Cartão de Crédito",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            uiState.invoiceUi?.let { invoice ->

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = invoice.status.color.copy(alpha = 0.15f),
                        contentColor = invoice.status.color,
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text(
                        text = "Fatura ${invoice.status.label}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 6.dp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Details

            uiState.invoiceUi?.let {
                DetailRow(
                    label = "Fatura Atual",
                    value = it.amount.toMoneyFormat(),
                    valueColor = Expense
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            DetailRow(
                label = "Limite",
                value = uiState.creditCard.limit.toMoneyFormat()
            )

            Spacer(modifier = Modifier.height(16.dp))

            val availableLimit = uiState.invoiceUi
                ?.availableLimit
                ?.toMoneyFormat()

            val limit = uiState.creditCard.limit.toMoneyFormat()

            DetailRow(
                label = "Limite Disponível",
                value = availableLimit ?: limit,
                valueColor = Income
            )

            uiState.invoiceUi?.let { invoice ->
                DetailRow(
                    label = "Fechamento",
                    value = creditCard.closingDay
                        ?.let { day -> formats.dayMonthYear.format(invoice.closingMonth.onDay(day)) }
                        ?: formats.yearMonth.format(invoice.closingMonth),
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
            )

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        modalManager.show(DeleteCreditCardModal(uiState.creditCard))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Expense,
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = Expense.copy(alpha = 0.5f),
                    ),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Excluir",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                OutlinedButton(
                    onClick = {
                        modalManager.show(
                            EditCreditCardModal(
                                creditCard = uiState.creditCard
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Info,
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = Info.copy(alpha = 0.5f),
                    ),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Editar",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            uiState.invoiceUi?.let { ui ->
                val currentMonth = Clock.System.now().toYearMonth()
                val isInClosingMonth = currentMonth >= ui.closingMonth

                when (ui.status) {
                    Invoice.Status.OPEN -> {
                        if (isInClosingMonth) {
                            OutlinedButton(
                                onClick = { modalManager.show(CloseInvoiceModal(ui.invoice)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFFFA726)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    Color(0xFFFFA726).copy(alpha = 0.5f)
                                ),
                                contentPadding = PaddingValues(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = "Fechar Fatura",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        OutlinedButton(
                            onClick = {
                                modalManager.show(
                                    AdvancePaymentModal(
                                        invoice = ui.invoice,
                                        currentBillAmount = ui.amount,
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colorScheme.primary
                            ),
                            border = BorderStroke(
                                1.dp,
                                colorScheme.primary.copy(alpha = 0.5f)
                            ),
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payment,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Antecipar Pagamento",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Invoice.Status.CLOSED -> {
                        Button(
                            onClick = {
                                modalManager.show(
                                    PayInvoiceModal(
                                        invoice = ui.invoice,
                                        currentBillAmount = ui.amount
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payment,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Pagar Fatura",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { modalManager.show(ReopenInvoiceModal(ui.id)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFFFA726)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFFFA726).copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Reabrir Fatura",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Invoice.Status.PAID -> Unit
                }
            } ?: run {
                Button(
                    onClick = {
                        modalManager.show(
                            OpenInvoiceModal(uiState.creditCard)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Abrir Fatura",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    @Composable
    private fun DetailRow(
        label: String,
        value: String,
        modifier: Modifier = Modifier,
        valueColor: Color = colorScheme.onSurface
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
    }
}
