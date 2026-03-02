package com.neoutils.finsight.ui.modal.categoryForm

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
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
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.category_form_edit_title
import com.neoutils.finsight.resources.category_form_expense
import com.neoutils.finsight.resources.category_form_icon_helper
import com.neoutils.finsight.resources.category_form_icon_modal_title
import com.neoutils.finsight.resources.category_form_icon_select
import com.neoutils.finsight.resources.category_form_income
import com.neoutils.finsight.resources.category_form_name_label
import com.neoutils.finsight.resources.category_form_new_title
import com.neoutils.finsight.resources.category_form_save
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.ui.util.stringUiText
import com.neoutils.finsight.util.CategoryIcon
import com.neoutils.finsight.util.Validation
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class CategoryFormModal(
    private val category: Category? = null,
    private val initialType: Category.Type? = null,
) : ModalBottomSheet() {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun ColumnScope.BottomSheetContent() {

        val viewModel = koinViewModel<CategoryFormViewModel> { parametersOf(category, initialType) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        val name = rememberTextFieldState(uiState.name)
        var isIconPickerVisible by remember { mutableStateOf(false) }

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

            IconSelector(
                selectedIcon = uiState.selectedIcon,
                selectedType = uiState.selectedType,
                onClick = { isIconPickerVisible = true }
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

        if (isIconPickerVisible) {
            IconPickerBottomSheet(
                icons = CategoryIcon.entries,
                selectedIcon = uiState.selectedIcon,
                selectedType = uiState.selectedType,
                onDismiss = { isIconPickerVisible = false },
                onIconSelected = { icon ->
                    viewModel.onAction(CategoryFormAction.IconChanged(icon))
                    isIconPickerVisible = false
                }
            )
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
    private fun IconSelector(
        selectedIcon: CategoryIcon,
        selectedType: Category.Type,
        onClick: () -> Unit
    ) {
        val categoryColor = if (selectedType.isIncome) Income else Expense

        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = categoryColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = selectedIcon.icon,
                            contentDescription = selectedIcon.name,
                            tint = categoryColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.category_form_icon_select),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(Res.string.category_form_icon_helper),
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun IconPickerBottomSheet(
        icons: List<CategoryIcon>,
        selectedIcon: CategoryIcon,
        selectedType: Category.Type,
        onDismiss: () -> Unit,
        onIconSelected: (CategoryIcon) -> Unit
    ) {
        val categoryColor = if (selectedType.isIncome) Income else Expense

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.category_form_icon_modal_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
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

                    Spacer(Modifier.height(8.dp))
                }
            }
        )
    }
}
