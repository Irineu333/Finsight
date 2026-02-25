package com.neoutils.finsight.ui.modal.deleteBudget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.ui.component.ModalBottomSheet
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class DeleteBudgetModal(
    private val budget: Budget,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<DeleteBudgetViewModel> { parametersOf(budget) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Excluir Orçamento",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tem certeza que deseja excluir o orçamento \"${budget.title}\"? Esta ação não pode ser desfeita.",
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.deleteBudget() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.error,
                ),
            ) {
                Text(
                    text = "Excluir",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
