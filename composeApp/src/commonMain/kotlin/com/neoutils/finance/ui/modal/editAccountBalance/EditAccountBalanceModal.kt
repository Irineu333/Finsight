package com.neoutils.finance.ui.modal.editAccountBalance

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.extension.toMoneyFormatWithSign
import com.neoutils.finance.ui.component.AccountSelector
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.ui.theme.Adjustment
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import com.neoutils.finance.ui.theme.TextLight1
import com.neoutils.finance.util.DateFormats
import com.neoutils.finance.util.MoneyInputTransformation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.YearMonth
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
class EditAccountBalanceModal(
    private val type: Type,
    private val targetMonth: YearMonth? = null,
    private val account: Account,
) : ModalBottomSheet() {

    private val formats = DateFormats()

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<EditAccountBalanceViewModel> {
            parametersOf(type, targetMonth, account)
        }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        val initialCents = (uiState.currentBalance * 100).toLong()
        val balanceState = rememberTextFieldState(formatMoney(initialCents))

        val newBalance by remember {
            derivedStateOf {
                parseMoneyToDouble(balanceState.text.toString())
            }
        }

        val adjustment by remember {
            derivedStateOf {
                newBalance - uiState.currentBalance
            }
        }

        LaunchedEffect(Unit) {
            snapshotFlow {
                uiState.currentBalance
            }.collectLatest {
                balanceState.edit {
                    replace(0, length, formatMoney((uiState.currentBalance * 100).toLong()))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = type.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )

            if (targetMonth != null) {
                Text(
                    text = formats.yearMonth.format(targetMonth),
                    fontSize = 14.sp,
                    color = TextLight1,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = adjustment,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { adjustment ->
                if (adjustment != 0.0) {
                    AdjustmentCard(
                        adjustment = adjustment,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth())
                }
            }

            AccountSelector(
                selectedAccount = uiState.selectedAccount,
                accounts = uiState.accounts,
                onAccountSelected = { account ->
                    account?.let { viewModel.selectAccount(it) }
                },
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
            )

            OutlinedTextField(
                label = { Text("Saldo") },
                state = balanceState,
                inputTransformation = MoneyInputTransformation(),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.adjustBalance(newBalance) },
                enabled = balanceState.text.isNotBlank() && newBalance != uiState.currentBalance,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Adjustment),
            ) {
                Text(
                    text = "Salvar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    @Composable
    private fun AdjustmentCard(
        adjustment: Double,
        modifier: Modifier = Modifier
    ) {
        val isIncome = adjustment > 0
        val color = if (isIncome) Income else Expense
        val icon = if (isIncome) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
        val label = if (isIncome) "Recebeu" else "Gastou"

        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = color.copy(alpha = 0.1f),
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(text = label, fontSize = 16.sp)
                }

                Text(
                    text = adjustment.toMoneyFormatWithSign(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }

    private fun formatMoney(cents: Long): String {
        val isNegative = cents < 0
        val absoluteCents = abs(cents)
        val reais = absoluteCents / 100
        val centavos = absoluteCents % 100
        val reaisFormatted = reais.toString()
            .reversed()
            .chunked(3)
            .joinToString(".")
            .reversed()
        val formatted = "R$ $reaisFormatted,${centavos.toString().padStart(2, '0')}"
        return if (isNegative) "-$formatted" else formatted
    }

    private fun parseMoneyToDouble(formatted: String): Double {
        val isNegative = formatted.startsWith("-")
        val digitsOnly = formatted
            .replace("-", "")
            .replace("R$", "")
            .replace(".", "")
            .replace(",", ".")
            .trim()
        val value = digitsOnly.toDoubleOrNull() ?: 0.0
        return if (isNegative) -value else value
    }

    enum class Type(val title: String) {
        CURRENT("Editar Saldo Atual"),
        FINAL("Editar Saldo Final"),
        INITIAL("Editar Saldo Inicial")
    }
}
