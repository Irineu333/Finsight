package com.neoutils.finsight.feature.accounts.modal.accountForm

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
import com.neoutils.finsight.feature.accounts.resources.Res
import com.neoutils.finsight.feature.accounts.resources.account_form_default_label
import com.neoutils.finsight.feature.accounts.resources.account_form_default_state_disabled
import com.neoutils.finsight.feature.accounts.resources.account_form_default_state_off
import com.neoutils.finsight.feature.accounts.resources.account_form_default_state_on
import com.neoutils.finsight.feature.accounts.resources.account_form_edit_title
import com.neoutils.finsight.feature.accounts.resources.account_form_icon_helper
import com.neoutils.finsight.feature.accounts.resources.account_form_icon_label
import com.neoutils.finsight.feature.accounts.resources.account_form_icon_modal_title
import com.neoutils.finsight.feature.accounts.resources.account_form_name_label
import com.neoutils.finsight.feature.accounts.resources.account_form_new_title
import com.neoutils.finsight.feature.accounts.resources.account_form_save
import com.neoutils.finsight.feature.accounts.resources.account_form_unavailable
import com.neoutils.finsight.core.ui.component.IconPickerSelector
import com.neoutils.finsight.core.ui.component.LocalModalManager
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.core.ui.component.ModalErrorContent
import com.neoutils.finsight.core.ui.modal.iconPicker.IconPickerModal
import com.neoutils.finsight.core.ui.util.FeatureIconCatalog
import com.neoutils.finsight.core.ui.util.Validation
import com.neoutils.finsight.core.ui.util.stringUiText
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class AccountFormModal(
    private val accountId: Long? = null,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {

        val viewModel = koinViewModel<AccountFormViewModel> { parametersOf(accountId) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        when (val state = uiState) {
            AccountFormUiState.Loading -> LoadingContent()
            AccountFormUiState.Error -> ErrorContent()
            is AccountFormUiState.Content -> Content(
                state = state,
                onAction = viewModel::onAction,
            )
        }
    }

    @Composable
    private fun ErrorContent() {
        val manager = LocalModalManager.current
        ModalErrorContent(
            message = stringResource(Res.string.account_form_unavailable),
            onClose = { manager.dismiss() },
        )
    }

    @Composable
    private fun LoadingContent() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.account_form_edit_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(96.dp))
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(96.dp))
        }
    }

    @Composable
    private fun Content(
        state: AccountFormUiState.Content,
        onAction: (AccountFormAction) -> Unit,
    ) {
        val modalManager = LocalModalManager.current
        val accentColor = MaterialTheme.colorScheme.primary
        val iconModalTitle = stringResource(Res.string.account_form_icon_modal_title)

        val name = rememberTextFieldState(state.form.name)

        LaunchedEffect(Unit) {
            snapshotFlow { name.text.toString() }
                .drop(1)
                .collect { newName ->
                    onAction(AccountFormAction.NameChanged(newName))
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
                text = if (state.isEditMode) {
                    stringResource(Res.string.account_form_edit_title)
                } else {
                    stringResource(Res.string.account_form_new_title)
                },
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                state = name,
                label = {
                    Text(text = stringResource(Res.string.account_form_name_label))
                },
                trailingIcon = when (state.validation[AccountField.NAME]) {
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
                isError = state.validation[AccountField.NAME] is Validation.Error,
                supportingText = when (val validation = state.validation[AccountField.NAME]) {
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
                    checked = state.form.isDefault,
                    canChange = state.canChangeDefault,
                    onCheckedChange = { onAction(AccountFormAction.IsDefaultChanged(it)) },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            IconPickerSelector(
                selectedIcon = state.form.icon,
                accentColor = accentColor,
                title = stringResource(Res.string.account_form_icon_label),
                helperText = stringResource(Res.string.account_form_icon_helper),
                onClick = {
                    modalManager.show(
                        IconPickerModal(
                            title = iconModalTitle,
                            selectedIcon = state.form.icon,
                            accentColor = accentColor,
                            icons = FeatureIconCatalog.withGeneral(
                                featureIcons = FeatureIconCatalog.accounts,
                                selectedIcon = state.form.icon,
                            ),
                            onIconSelected = { icon ->
                                onAction(AccountFormAction.IconSelected(icon))
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
                    onAction(AccountFormAction.Submit)
                },
                enabled = state.canSubmit,
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
