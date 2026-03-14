package com.neoutils.finsight.ui.modal.goalForm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Goal
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.goal_form_edit_title
import com.neoutils.finsight.resources.goal_form_icon_helper
import com.neoutils.finsight.resources.goal_form_icon_label
import com.neoutils.finsight.resources.goal_form_icon_modal_title
import com.neoutils.finsight.resources.goal_form_new_title
import com.neoutils.finsight.resources.goal_form_save
import com.neoutils.finsight.resources.goal_form_target_label
import com.neoutils.finsight.resources.goal_form_title_label
import com.neoutils.finsight.ui.component.IconPickerSelector
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.component.MultiCategorySelector
import com.neoutils.finsight.ui.modal.iconPicker.IconPickerModal
import com.neoutils.finsight.util.FeatureIconCatalog
import com.neoutils.finsight.util.Validation
import com.neoutils.finsight.util.rememberMoneyInputTransformation
import com.neoutils.finsight.util.stringUiText
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class GoalFormModal(
    private val goal: Goal? = null,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val formatter = LocalCurrencyFormatter.current
        val viewModel = koinViewModel<GoalFormViewModel> { parametersOf(goal) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val modalManager = LocalModalManager.current
        val accentColor = MaterialTheme.colorScheme.primary
        val iconModalTitle = stringResource(Res.string.goal_form_icon_modal_title)

        val amount = rememberTextFieldState(goal?.amount?.let { formatter.format(it) } ?: "")

        LaunchedEffect(Unit) {
            snapshotFlow { amount.text.toString() }.collect {
                viewModel.onAction(GoalFormAction.AmountChanged(it))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (uiState.isEditMode) {
                    stringResource(Res.string.goal_form_edit_title)
                } else {
                    stringResource(Res.string.goal_form_new_title)
                },
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.onAction(GoalFormAction.TitleChanged(it)) },
                label = { Text(text = stringResource(Res.string.goal_form_title_label)) },
                trailingIcon = when (uiState.validation[GoalField.TITLE]) {
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
                isError = uiState.validation[GoalField.TITLE] is Validation.Error,
                supportingText = when (val validation = uiState.validation[GoalField.TITLE]) {
                    is Validation.Error -> {
                        {
                            Text(text = stringUiText(validation.error))
                        }
                    }

                    else -> null
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            MultiCategorySelector(
                selectedCategories = uiState.selectedCategories,
                categories = uiState.availableCategories,
                onCategoryToggled = { viewModel.onAction(GoalFormAction.CategoryToggled(it)) },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                state = amount,
                label = { Text(text = stringResource(Res.string.goal_form_target_label)) },
                inputTransformation = rememberMoneyInputTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            IconPickerSelector(
                selectedIcon = uiState.selectedIcon,
                accentColor = accentColor,
                title = stringResource(Res.string.goal_form_icon_label),
                helperText = stringResource(Res.string.goal_form_icon_helper),
                onClick = {
                    modalManager.show(
                        IconPickerModal(
                            title = iconModalTitle,
                            selectedIcon = uiState.selectedIcon,
                            accentColor = accentColor,
                            icons = FeatureIconCatalog.withGeneral(
                                featureIcons = FeatureIconCatalog.goals,
                                selectedIcon = uiState.selectedIcon,
                            ),
                            onIconSelected = { icon ->
                                viewModel.onAction(GoalFormAction.IconSelected(icon))
                            },
                        )
                    )
                },
            )

            HorizontalDivider()

            Button(
                onClick = { viewModel.onAction(GoalFormAction.Submit) },
                enabled = uiState.canSubmit,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.goal_form_save),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
