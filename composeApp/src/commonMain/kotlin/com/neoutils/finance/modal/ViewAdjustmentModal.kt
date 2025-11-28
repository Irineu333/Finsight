@file:OptIn(ExperimentalMaterial3Api::class, FormatStringsInDatetimeFormats::class)

package com.neoutils.finance.modal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.data.TransactionEntry
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.manager.LocalModalManager
import com.neoutils.finance.manager.Modal
import com.neoutils.finance.ui.theme.Adjustment
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern

class ViewAdjustmentModal(
    private val transaction: TransactionEntry
) : Modal {

    private val dateFormat = LocalDate.Format {
        byUnicodePattern("dd/MM/yyyy")
    }

    @Composable
    override fun Content() {
        val manager = LocalModalManager.current

        ModalBottomSheet(
            onDismissRequest = {
                manager.dismiss()
            },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Ajuste de Saldo",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Adjustment
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = transaction.description,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                DetailRow(
                    label = "Valor Ajustado",
                    value = transaction.amount.toMoneyFormat(),
                    valueColor = Adjustment
                )

                Spacer(modifier = Modifier.height(8.dp))

                DetailRow(
                    label = "Data",
                    value = dateFormat.format(transaction.date)
                )

                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                OutlinedButton(
                    onClick = {
                        manager.show(DeleteConfirmationModal(transaction))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.error,
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Excluir Ajuste",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    @Composable
    private fun DetailRow(
        label: String,
        value: String,
        valueColor: Color = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
