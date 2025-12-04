@file:OptIn(ExperimentalMaterial3Api::class, FormatStringsInDatetimeFormats::class)

package com.neoutils.finance.ui.modal.viewTransaction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.ui.component.CategoryIconBox
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.ui.modal.DeleteTransactionModal
import com.neoutils.finance.ui.modal.EditTransactionModal
import com.neoutils.finance.ui.theme.Adjustment
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import com.neoutils.finance.ui.theme.Info
import com.neoutils.finance.util.DateFormats
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class ViewTransactionModal(
    private val transaction: Transaction
) : ModalBottomSheet {

    private val formats = DateFormats()

    private val key = transaction.id.toString()

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val manager = LocalModalManager.current

        val viewModel = koinViewModel<ViewTransactionViewModel>(key = key) { parametersOf(transaction) }

        val uiState by viewModel.uiState.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    uiState.category?.let {
                        CategoryIconBox(
                            category = it,
                            modifier = Modifier.size(64.dp),
                            contentPadding = PaddingValues(16.dp),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    Column {
                        Text(
                            text = when (uiState.transaction.type) {
                                Transaction.Type.INCOME -> "Receita"
                                Transaction.Type.EXPENSE -> "Despesa"
                                Transaction.Type.ADJUSTMENT -> "Ajuste"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = when (uiState.transaction.type) {
                                Transaction.Type.INCOME -> Income
                                Transaction.Type.EXPENSE -> Expense
                                Transaction.Type.ADJUSTMENT -> Adjustment
                            }
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                DetailRow(
                    label = "Valor",
                    value = uiState.transaction.amount.toMoneyFormat(),
                    valueColor = when (uiState.transaction.type) {
                        Transaction.Type.INCOME -> Income
                        Transaction.Type.EXPENSE -> Expense
                        Transaction.Type.ADJUSTMENT -> Adjustment
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                DetailRow(
                    label = "Data",
                    value = formats.dayMonthYear.format(uiState.transaction.date)
                )

                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            manager.show(DeleteTransactionModal(uiState.transaction))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colorScheme.error,
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = colorScheme.error,
                        )
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
                            manager.show(EditTransactionModal(uiState.transaction))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Info,
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = Info,
                        )
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
            }
        }

    @Composable
    private fun DetailRow(
        label: String,
        value: String,
        valueColor: Color = colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            )
        }
    }
}
