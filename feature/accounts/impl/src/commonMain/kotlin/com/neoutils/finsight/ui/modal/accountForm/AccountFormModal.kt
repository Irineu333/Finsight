package com.neoutils.finsight.ui.modal.accountForm

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.feature.accounts.impl.resources.Res
import com.neoutils.finsight.feature.accounts.impl.resources.account_form_default_label
import com.neoutils.finsight.feature.accounts.impl.resources.account_form_default_state_disabled
import com.neoutils.finsight.feature.accounts.impl.resources.account_form_default_state_off
import com.neoutils.finsight.feature.accounts.impl.resources.account_form_default_state_on
import com.neoutils.finsight.feature.accounts.impl.resources.account_form_edit_title
import com.neoutils.finsight.feature.accounts.impl.resources.account_form_icon_helper
import com.neoutils.finsight.feature.accounts.impl.resources.account_form_icon_label
import com.neoutils.finsight.feature.accounts.impl.resources.account_form_icon_modal_title
import com.neoutils.finsight.feature.accounts.impl.resources.account_form_name_label
import com.neoutils.finsight.feature.accounts.impl.resources.account_form_new_title
import com.neoutils.finsight.feature.accounts.impl.resources.account_form_save
import com.neoutils.finsight.ui.component.IconPickerSelector
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.iconPicker.IconPickerModal
import com.neoutils.finsight.util.FeatureIconCatalog
import com.neoutils.finsight.util.Validation
import com.neoutils.finsight.util.stringUiText
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class AccountFormModal(
    private val account: Account? = null,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {

        val viewModel = koinViewModel<AccountFormViewModel> { parametersOf(account) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val modalManager = LocalModalManager.current
        val accentColor = MaterialTheme.colorScheme.primary
        val iconModalTitle = stringResource(Res.string.account_form_icon_modal_title)

        val name = rememberTextFieldState(uiState.name)

        LaunchedEffect(Unit) {
            snapshotFlow { name.text.toString() }
                .drop(1)
                .collect { name ->
                    viewModel.onAction(AccountFormAction.NameChanged(name))
                }
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
                text = if (uiState.isEditMode) stringResource(Res.string.account_form_edit_title) else stringResource(Res.string.account_form_new_title),
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                state = name,
                label = {
                    Text(text = stringResource(Res.string.account_form_name_label))
                },
                trailingIcon = when (uiState.validation[AccountField.NAME]) {
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
                isError = uiState.validation[AccountField.NAME] is Validation.Error,
                supportingText = when (val validation = uiState.validation[AccountField.NAME]) {
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

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                DefaultAccountSelector(
                    checked = uiState.isDefault,
                    canChange = uiState.canChangeDefault,
                    onCheckedChange = { viewModel.onAction(AccountFormAction.IsDefaultChanged(it)) },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            IconPickerSelector(
                selectedIcon = uiState.selectedIcon,
                accentColor = accentColor,
                title = stringResource(Res.string.account_form_icon_label),
                helperText = stringResource(Res.string.account_form_icon_helper),
                onClick = {
                    modalManager.show(
                        IconPickerModal(
                            title = iconModalTitle,
                            selectedIcon = uiState.selectedIcon,
                            accentColor = accentColor,
                            icons = FeatureIconCatalog.withGeneral(
                                featureIcons = FeatureIconCatalog.accounts,
                                selectedIcon = uiState.selectedIcon,
                            ),
                            onIconSelected = { icon ->
                                viewModel.onAction(AccountFormAction.IconSelected(icon))
                            },
                        )
                    )
                },
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Button(
                onClick = {
                    viewModel.onAction(AccountFormAction.Submit)
                },
                enabled = uiState.canSubmit,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.account_form_save),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DefaultAccountSelector(
    checked: Boolean,
    canChange: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val accentColor = MaterialTheme.colorScheme.primary
    val subtitle = when {
        !canChange -> stringResource(Res.string.account_form_default_state_disabled)
        checked -> stringResource(Res.string.account_form_default_state_on)
        else -> stringResource(Res.string.account_form_default_state_off)
    }

    Surface(
        onClick = {
            if (canChange) {
                onCheckedChange(!checked)
            }
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = if (canChange) 0.12f else 0.08f),
                modifier = Modifier.size(52.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (canChange) {
                            accentColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(Res.string.account_form_default_label),
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Switch(
                checked = checked,
                enabled = canChange,
                colors = SwitchDefaults.colors(
                    uncheckedThumbColor = Color.LightGray,
                ),
                onCheckedChange = onCheckedChange,
            )
        }
    }
}
