@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, FormatStringsInDatetimeFormats::class)

package com.neoutils.finance.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.then
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.manager.LocalModalManager
import com.neoutils.finance.manager.Modal
import com.neoutils.finance.manager.ModalManager
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.ExpenseCardBackground
import com.neoutils.finance.ui.theme.Income
import com.neoutils.finance.ui.theme.TextLight2
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AddTransactionModal(
    private val onSave: (Transaction) -> Unit,
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

                var type by remember { mutableStateOf(Transaction.Type.INCOME) }
                val amount = rememberTextFieldState()
                val description = rememberTextFieldState()
                var selectedDate by remember { mutableStateOf(currentDate()) }

                val modal = remember { ModalManager() }

                TypeToggle(
                    selectedType = type,
                    onTypeSelected = {
                        type = it
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    state = amount,
                    label = {
                        Text(text = "Amount")
                    },
                    inputTransformation = MoneyInputTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    state = description,
                    label = {
                        Text(text = "Description")
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = dateFormat.format(selectedDate),
                    onValueChange = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(selectedDate) {
                            awaitEachGesture {
                                awaitFirstDown(pass = PointerEventPass.Initial)
                                waitForUpOrCancellation(
                                    pass = PointerEventPass.Initial
                                )?.let {
                                    modal.show(
                                        DatePickerModal(
                                            initialDate = selectedDate,
                                            onDateSelected = { date ->
                                                selectedDate = date
                                            }
                                        )
                                    )
                                }
                            }
                        },
                    readOnly = true,
                    label = {
                        Text(text = "Date")
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.TwoTone.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onSave(
                            Transaction(
                                type = type,
                                amount = parseMoneyToDouble(amount.text.toString()),
                                description = description.text.toString().ifEmpty { null },
                                date = selectedDate,
                            )
                        )

                        manager.dismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Salvar",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                modal.Content()
            }
        }
    }

    @Composable
    fun TypeToggle(
        selectedType: Transaction.Type,
        onTypeSelected: (Transaction.Type) -> Unit
    ) = Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { onTypeSelected(Transaction.Type.EXPENSE) },
            modifier = Modifier.weight(1f),
            colors = when (selectedType) {
                Transaction.Type.EXPENSE -> {
                    ButtonDefaults.buttonColors(
                        containerColor = Expense,
                        contentColor = Color.White
                    )
                }

                Transaction.Type.INCOME -> {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Despesa",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Button(
            onClick = { onTypeSelected(Transaction.Type.INCOME) },
            modifier = Modifier.weight(1f),
            colors = when (selectedType) {
                Transaction.Type.INCOME -> {
                    ButtonDefaults.buttonColors(
                        containerColor = Income,
                        contentColor = Color.White
                    )
                }

                Transaction.Type.EXPENSE -> {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Receita",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    private fun currentDate(): LocalDate {
        return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    private fun parseMoneyToDouble(formatted: String): Double {
        // Remove "R$ ", pontos de milhar e substitui vírgula por ponto
        val digitsOnly = formatted
            .replace("R$", "")
            .replace(".", "")
            .replace(",", ".")
            .trim()

        return digitsOnly.toDoubleOrNull() ?: 0.0
    }

    data class Transaction(
        val type: Type,
        val amount: Double,
        val description: String?,
        val date: LocalDate,
    ) {
        enum class Type {
            EXPENSE,
            INCOME
        }
    }
}
