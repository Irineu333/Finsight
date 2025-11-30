@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.neoutils.finance.ui.modal

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.data.Category
import com.neoutils.finance.data.CategoryRepository
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.Modal
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.ui.icons.CategoryIcon
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class AddCategoryModal(
    private val initialType: Category.CategoryType = Category.CategoryType.EXPENSE
) : ModalBottomSheet {

    @Composable
    override fun ColumnScope.BottomSheet() {

        val repository = koinInject<CategoryRepository>()
        val manager = LocalModalManager.current
        val scope = rememberCoroutineScope()

        val name = rememberTextFieldState()
        var selectedIcon by remember { mutableStateOf(CategoryIcon.SHOPPING_CART) }
        var selectedType by remember { mutableStateOf(initialType) }

        ModalBottomSheet(
            onDismissRequest = { manager.dismiss() },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Nova Categoria",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )

                TypeToggle(
                    selectedType = selectedType,
                    onTypeSelected = { selectedType = it }
                )

                OutlinedTextField(
                    state = name,
                    label = {
                        Text(text = "Nome")
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(12.dp),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Ícone",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )

                IconGrid(
                    icons = CategoryIcon.entries,
                    selectedIcon = selectedIcon,
                    selectedType = selectedType,
                    onIconSelected = { selectedIcon = it }
                )

                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                Button(
                    onClick = {
                        scope.launch {
                            repository.insert(
                                Category(
                                    name = name.text.toString(),
                                    key = selectedIcon.key,
                                    type = selectedType
                                )
                            )
                            manager.dismiss()
                        }
                    },
                    enabled = name.text.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Salvar",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    @Composable
    private fun TypeToggle(
        selectedType: Category.CategoryType,
        onTypeSelected: (Category.CategoryType) -> Unit
    ) = Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { onTypeSelected(Category.CategoryType.EXPENSE) },
            modifier = Modifier.weight(1f),
            colors = when (selectedType) {
                Category.CategoryType.EXPENSE -> {
                    ButtonDefaults.buttonColors(
                        containerColor = Expense,
                        contentColor = Color.White
                    )
                }

                Category.CategoryType.INCOME -> {
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
            onClick = { onTypeSelected(Category.CategoryType.INCOME) },
            modifier = Modifier.weight(1f),
            colors = when (selectedType) {
                Category.CategoryType.INCOME -> {
                    ButtonDefaults.buttonColors(
                        containerColor = Income,
                        contentColor = Color.White
                    )
                }

                Category.CategoryType.EXPENSE -> {
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

    @Composable
    private fun IconGrid(
        icons: List<CategoryIcon>,
        selectedIcon: CategoryIcon,
        selectedType: Category.CategoryType,
        onIconSelected: (CategoryIcon) -> Unit
    ) {
        val categoryColor = if (selectedType.isIncome) Income else Expense

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icons.forEach { icon ->
                val isSelected = icon == selectedIcon
                Surface(
                    onClick = { onIconSelected(icon) },
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .size(64.dp)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = categoryColor,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            } else Modifier
                        )
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = icon.icon,
                            contentDescription = icon.name,
                            tint = if (isSelected) categoryColor else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
