package com.neoutils.finance.ui.modal.deleteCategory

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
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.ui.component.ModalBottomSheet
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class DeleteCategoryModal(
    private val category: Category
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<DeleteCategoryViewModel>(key = key) { parametersOf(category) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Excluir Categoria",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tem certeza que deseja excluir esta categoria? As transações associadas não serão excluídas.",
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.deleteCategory()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.error
                )
            ) {
                Text(
                    text = "Excluir",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}