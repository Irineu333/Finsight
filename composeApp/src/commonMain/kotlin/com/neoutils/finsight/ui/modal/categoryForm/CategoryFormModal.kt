package com.neoutils.finsight.ui.modal.categoryForm

import androidx.compose.animation.animateContentSize
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.accountForm.AccountField
import com.neoutils.finsight.util.Validation
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.ui.util.stringUiText
import com.neoutils.finsight.util.CategoryIcon
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.category_form_edit_title
import com.neoutils.finsight.resources.category_form_expense
import com.neoutils.finsight.resources.category_form_icon_label
import com.neoutils.finsight.resources.category_form_income
import com.neoutils.finsight.resources.category_form_name_label
import com.neoutils.finsight.resources.category_form_new_title
import com.neoutils.finsight.resources.category_form_save
import com.neoutils.finsight.resources.category_form_see_less
import com.neoutils.finsight.resources.category_form_see_more
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class CategoryFormModal(
    private val category: Category? = null,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {

        val viewModel = koinViewModel<CategoryFormViewModel> { parametersOf(category) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        val name = rememberTextFieldState(uiState.name)
        var isIconGridExpanded by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            snapshotFlow { name.text.toString() }
                .drop(1)
                .collect { name ->
                    viewModel.onAction(CategoryFormAction.NameChanged(name))
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
                text = if (uiState.isEditMode) stringResource(Res.string.category_form_edit_title) else stringResource(Res.string.category_form_new_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )

            if (!uiState.isEditMode) {
                TypeToggle(
                    selectedType = uiState.selectedType,
                    onTypeSelected = { type ->
                        viewModel.onAction(CategoryFormAction.TypeChanged(type))
                    }
                )
            }

            OutlinedTextField(
                state = name,
                label = {
                    Text(text = stringResource(Res.string.category_form_name_label))
                },
                trailingIcon = when (uiState.validation[CategoryField.NAME]) {
                    Validation.Validating -> {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    else -> null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                isError = uiState.validation[CategoryField.NAME]is Validation.Error,
                supportingText = when (val validation = uiState.validation[CategoryField.NAME]) {
                    is Validation.Error -> {
                        {
                            Text(text = stringUiText(validation.error))
                        }
                    }

                    else -> null
                },
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier
                    .animateContentSize()
                    .fillMaxWidth(),
            )

            Text(
                text = stringResource(Res.string.category_form_icon_label),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

            IconGrid(
                icons = CategoryIcon.entries,
                selectedIcon = uiState.selectedIcon,
                selectedType = uiState.selectedType,
                isExpanded = isIconGridExpanded,
                onIconSelected = { icon ->
                    viewModel.onAction(CategoryFormAction.IconChanged(icon))
                },
                onToggleExpand = { isIconGridExpanded = !isIconGridExpanded }
            )

            HorizontalDivider()

            Button(
                onClick = {
                    viewModel.onAction(CategoryFormAction.Submit)
                },
                enabled = uiState.canSubmit,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.category_form_save),
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
                text = stringResource(Res.string.category_form_expense),
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
                text = stringResource(Res.string.category_form_income),
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
        val categoryColor = if (selectedType.isIncome) Income else Expense

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
                    text = if (isExpanded) stringResource(Res.string.category_form_see_less) else stringResource(Res.string.category_form_see_more, hiddenCount),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
