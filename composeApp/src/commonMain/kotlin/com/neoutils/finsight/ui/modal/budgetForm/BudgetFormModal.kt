@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.modal.budgetForm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.extension.toMoneyFormat
import com.neoutils.finsight.ui.component.CategoryIconBox
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.component.MultiCategorySelector
import com.neoutils.finsight.util.MoneyInputTransformation
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.budget_form_edit_title
import com.neoutils.finsight.resources.budget_form_icon_label
import com.neoutils.finsight.resources.budget_form_limit_label
import com.neoutils.finsight.resources.budget_form_new_title
import com.neoutils.finsight.resources.budget_form_save
import com.neoutils.finsight.resources.budget_form_title_label
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class BudgetFormModal(
    private val budget: Budget? = null,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<BudgetFormViewModel> { parametersOf(budget) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        val amount = rememberTextFieldState(budget?.amount?.toMoneyFormat() ?: "")

        LaunchedEffect(Unit) {
            snapshotFlow { amount.text.toString() }.collect {
                viewModel.onAction(BudgetFormAction.AmountChanged(it))
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
                text = if (uiState.isEditMode) stringResource(Res.string.budget_form_edit_title) else stringResource(Res.string.budget_form_new_title),
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.onAction(BudgetFormAction.TitleChanged(it)) },
                label = { Text(text = stringResource(Res.string.budget_form_title_label)) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            MultiCategorySelector(
                selectedCategories = uiState.selectedCategories,
                categories = uiState.availableCategories,
                onCategoryToggled = { category ->
                    viewModel.onAction(BudgetFormAction.CategoryToggled(category))
                },
                modifier = Modifier.fillMaxWidth(),
            )

            AnimatedVisibility(visible = uiState.selectedCategories.size > 1) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.budget_form_icon_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(uiState.selectedCategories, key = { it.id }) { category ->
                            val isSelected = category.id == uiState.iconCategory?.id
                            Box {
                                CategoryIconBox(
                                    category = category,
                                    contentPadding = PaddingValues(10.dp),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .alpha(if (isSelected) 1f else 0.4f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .then(
                                            if (isSelected) Modifier.border(
                                                width = 2.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(12.dp),
                                            ) else Modifier
                                        )
                                        .clickable {
                                            viewModel.onAction(BudgetFormAction.IconCategorySelected(category.id))
                                        },
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(2.dp)
                                            .size(16.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(10.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                state = amount,
                label = { Text(text = stringResource(Res.string.budget_form_limit_label)) },
                inputTransformation = MoneyInputTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Button(
                onClick = { viewModel.onAction(BudgetFormAction.Submit) },
                enabled = uiState.canSubmit,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.budget_form_save),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
