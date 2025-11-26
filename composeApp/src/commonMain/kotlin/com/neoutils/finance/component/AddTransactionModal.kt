@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, FormatStringsInDatetimeFormats::class)

package com.neoutils.finance.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.then
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    color = Color(0xFF2D4A42)
                )
            },
            containerColor = Color(0xFF1E3A33),
            contentColor = Color.White
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
                        Text(
                            text = "Amount",
                            color = Color.White,
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2D4A42),
                        unfocusedBorderColor = Color(0xFF2D4A42),
                        focusedContainerColor = Color(0xFF2D4A42),
                        unfocusedContainerColor = Color(0xFF2D4A42),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    ),
                    inputTransformation = MoneyInputTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    state = description,
                    label = {
                        Text(
                            text = "Description",
                            color = Color.White,
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2D4A42),
                        unfocusedBorderColor = Color(0xFF2D4A42),
                        focusedContainerColor = Color(0xFF2D4A42),
                        unfocusedContainerColor = Color(0xFF2D4A42),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
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
                                val upEvent = waitForUpOrCancellation(pass = PointerEventPass.Initial)

                                if (upEvent != null) {
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
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2D4A42),
                        unfocusedBorderColor = Color(0xFF2D4A42),
                        focusedContainerColor = Color(0xFF2D4A42),
                        unfocusedContainerColor = Color(0xFF2D4A42),
                        disabledContainerColor = Color(0xFF2D4A42),
                        disabledBorderColor = Color(0xFF2D4A42),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = Color.White,
                        cursorColor = Color.White
                    ),
                    label = {
                        Text(
                            text = "Date",
                            color = Color.White,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = Color.White
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        contentColor = Color.White
                    ),
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
            colors = ButtonDefaults.buttonColors(
                containerColor = when (selectedType) {
                    Transaction.Type.EXPENSE -> Color(0xFFE53E3E)
                    Transaction.Type.INCOME -> Color(0xFF2D4A42)
                },
                contentColor = Color.White
            ),
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
            colors = ButtonDefaults.buttonColors(
                containerColor = when (selectedType) {
                    Transaction.Type.EXPENSE -> Color(0xFF2D4A42)
                    Transaction.Type.INCOME -> Color(0xFF4CAF50)
                },
                contentColor = Color.White
            ),
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
