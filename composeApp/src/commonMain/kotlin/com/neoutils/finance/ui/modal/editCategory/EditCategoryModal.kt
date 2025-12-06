package com.neoutils.finance.ui.modal.editCategory

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.ui.icons.CategoryIcon
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class EditCategoryModal(
    private val category: Category
) : ModalBottomSheet() {

    private val duplicatedNameError = @Composable {
        Text("Nome duplicado")
    }

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<EditCategoryViewModel>(key = key) { parametersOf(category) }

        val name = rememberTextFieldState(category.name)
        var selectedIcon by remember { mutableStateOf(CategoryIcon.fromKey(category.key)) }
        val selectedType = category.type

        val existingCategories by viewModel.existingCategories
            .collectAsState()

        val isDuplicateName by remember {
            derivedStateOf {
                existingCategories.any {
                    it.id != category.id &&
                            it.name.equals(name.text.toString().trim(), ignoreCase = true)
                }
            }
        }

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
                text = "Editar Categoria",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
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
                isError = isDuplicateName,
                supportingText = duplicatedNameError.takeIf { isDuplicateName },
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
                    viewModel.updateCategory(
                        updatedCategory = category.copy(
                            name = name.text.toString().trim(),
                            key = selectedIcon.key
                        )
                    )
                },
                enabled = name.text.isNotBlank() && !isDuplicateName,
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

    @Composable
    private fun IconGrid(
        icons: List<CategoryIcon>,
        selectedIcon: CategoryIcon,
        selectedType: Category.Type,
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
                    color = colorScheme.surfaceContainerHighest,
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
                            } else Modifier.Companion
                        )
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = icon.icon,
                            contentDescription = icon.name,
                            tint = if (isSelected) categoryColor else colorScheme.onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}