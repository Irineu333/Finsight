package com.neoutils.finsight.ui.modal.categoryForm

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
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
import com.neoutils.finsight.ui.component.IconPickerSelector
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.iconPicker.IconPickerModal
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.util.FeatureIconCatalog
import com.neoutils.finsight.util.Validation
import com.neoutils.finsight.util.stringUiText
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class CategoryFormModal(
    private val category: Category? = null,
    private val initialType: Category.Type? = null,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<CategoryFormViewModel> { parametersOf(category, initialType) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val modalManager = LocalModalManager.current

        val name = rememberTextFieldState(uiState.name)
        val accentColor = if (uiState.selectedType.isIncome) Income else Expense
        val iconModalTitle = stringResource(Res.string.category_form_icon_modal_title)

        LaunchedEffect(Unit) {
            snapshotFlow { name.text.toString() }
                .drop(1)
                .collect { viewModel.onAction(CategoryFormAction.NameChanged(it)) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (uiState.isEditMode) {
                    stringResource(Res.string.category_form_edit_title)
                } else {
                    stringResource(Res.string.category_form_new_title)
                },
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!uiState.isEditMode) {
                TypeToggle(
                    selectedType = uiState.selectedType,
                    onTypeSelected = { viewModel.onAction(CategoryFormAction.TypeChanged(it)) },
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                state = name,
                label = { Text(text = stringResource(Res.string.category_form_name_label)) },
                trailingIcon = when (uiState.validation[CategoryField.NAME]) {
                    Validation.Validating -> {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }

                    else -> null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                isError = uiState.validation[CategoryField.NAME] is Validation.Error,
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

            Spacer(modifier = Modifier.height(8.dp))

            IconPickerSelector(
                selectedIcon = uiState.selectedIcon,
                accentColor = accentColor,
                title = stringResource(Res.string.category_form_icon_select),
                helperText = stringResource(Res.string.category_form_icon_helper),
                onClick = {
                    modalManager.show(
                        IconPickerModal(
                            title = iconModalTitle,
                            selectedIcon = uiState.selectedIcon,
                            accentColor = accentColor,
                            icons = FeatureIconCatalog.withGeneral(
                                featureIcons = FeatureIconCatalog.categories,
                                selectedIcon = uiState.selectedIcon,
                            ),
                            onIconSelected = { icon ->
                                viewModel.onAction(CategoryFormAction.IconChanged(icon))
                            },
                        )
                    )
                },
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Button(
                onClick = { viewModel.onAction(CategoryFormAction.Submit) },
                enabled = uiState.canSubmit,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.category_form_save),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    @Composable
    private fun TypeToggle(
        selectedType: Category.Type,
        onTypeSelected: (Category.Type) -> Unit,
        modifier: Modifier = Modifier,
    ) = Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = { onTypeSelected(Category.Type.EXPENSE) },
            modifier = Modifier.weight(1f),
            colors = when (selectedType) {
                Category.Type.EXPENSE -> ButtonDefaults.buttonColors(
                    containerColor = Expense,
                    contentColor = Color.White,
                )

                Category.Type.INCOME -> ButtonDefaults.buttonColors(
                    containerColor = colorScheme.surfaceContainerHighest,
                    contentColor = colorScheme.onSurfaceVariant,
                )
            },
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.category_form_expense),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Button(
            onClick = { onTypeSelected(Category.Type.INCOME) },
            modifier = Modifier.weight(1f),
            colors = when (selectedType) {
                Category.Type.INCOME -> ButtonDefaults.buttonColors(
                    containerColor = Income,
                    contentColor = Color.White,
                )

                Category.Type.EXPENSE -> ButtonDefaults.buttonColors(
                    containerColor = colorScheme.surfaceContainerHighest,
                    contentColor = colorScheme.onSurfaceVariant,
                )
            },
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.category_form_income),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
