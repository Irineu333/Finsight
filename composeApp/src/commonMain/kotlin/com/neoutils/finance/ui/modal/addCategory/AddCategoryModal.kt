@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.addCategory

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.ui.icons.CategoryLazyIcon
import com.neoutils.finance.util.CategoryIcon
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AddCategoryModal(
    private val initialType: Category.Type = Category.Type.EXPENSE
) : ModalBottomSheet() {

    private val duplicatedNameError = @Composable {
        Text("Nome duplicado")
    }

    @Composable
    override fun ColumnScope.BottomSheetContent() {

        val viewModel = koinViewModel<AddCategoryViewModel>(key = key) { parametersOf(initialType) }

        val name = rememberTextFieldState()
        var selectedIcon by remember { mutableStateOf(CategoryIcon.SHOPPING_CART) }
        var selectedType by remember { mutableStateOf(initialType) }
        var isIconGridExpanded by remember { mutableStateOf(false) }

        val existingCategories by viewModel.existingCategories.collectAsState()

        val isDuplicateName by remember {
            derivedStateOf {
                existingCategories.any {
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
                isExpanded = isIconGridExpanded,
                onIconSelected = { selectedIcon = it },
                onToggleExpand = { isIconGridExpanded = !isIconGridExpanded }
            )

            HorizontalDivider()

            Button(
                onClick = {
                    viewModel.addCategory(
                        category = Category(
                            name = name.text.toString(),
                            icon = CategoryLazyIcon(selectedIcon.key),
                            type = selectedType,
                            createdAt = Clock.System.now().toEpochMilliseconds()
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
    private fun TypeToggle(
        selectedType: Category.Type,
        onTypeSelected: (Category.Type) -> Unit
    ) = Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { onTypeSelected(Category.Type.EXPENSE) },
            modifier = Modifier.weight(1f),
            colors = when (selectedType) {
                Category.Type.EXPENSE -> {
                    ButtonDefaults.buttonColors(
                        containerColor = Expense,
                        contentColor = Color.White
                    )
                }

                Category.Type.INCOME -> {
                    ButtonDefaults.buttonColors(
                        containerColor = colorScheme.surfaceContainerHighest,
                        contentColor = colorScheme.onSurfaceVariant
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
            onClick = { onTypeSelected(Category.Type.INCOME) },
            modifier = Modifier.weight(1f),
            colors = when (selectedType) {
                Category.Type.INCOME -> {
                    ButtonDefaults.buttonColors(
                        containerColor = Income,
                        contentColor = Color.White
                    )
                }

                Category.Type.EXPENSE -> {
                    ButtonDefaults.buttonColors(
                        containerColor = colorScheme.surfaceContainerHighest,
                        contentColor = colorScheme.onSurfaceVariant
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
        selectedType: Category.Type,
        isExpanded: Boolean,
        onIconSelected: (CategoryIcon) -> Unit,
        onToggleExpand: () -> Unit
    ) {
        val categoryColor = when (selectedType) {
            Category.Type.INCOME -> Income
            Category.Type.EXPENSE -> Expense
        }

        val visibleIcons = if (isExpanded) icons else icons.take(12)
        val hiddenCount = icons.size - 12

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visibleIcons.forEach { icon ->
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
                                tint = if (isSelected) categoryColor else colorScheme.onSurface,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onToggleExpand,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isExpanded) "Ver menos" else "Ver mais ($hiddenCount)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}