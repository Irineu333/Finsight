package com.neoutils.finsight.ui.modal.currencyForm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.CurrencyInfo
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.currency_form_code
import com.neoutils.finsight.resources.currency_form_code_helper
import com.neoutils.finsight.resources.currency_form_name
import com.neoutils.finsight.resources.currency_form_name_helper
import com.neoutils.finsight.resources.currency_form_save
import com.neoutils.finsight.resources.currency_form_symbol
import com.neoutils.finsight.resources.currency_form_title_edit
import com.neoutils.finsight.resources.currency_form_title_new
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.util.stringUiText
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Registers a currency, or edits one — three fields, and no fourth.
 *
 * **There is no control for decimal places**, and its absence is the design: every
 * currency the app stores has two, because the arithmetic of the whole app assumes base
 * 100. A code the platform declares to have zero or three is refused with the reason, at
 * the one place a currency comes into existence.
 *
 * The **code** is the identity and is not editable afterwards: it is denormalised across
 * accounts, entries, budgets and rates, so changing it would be a data migration rather
 * than an edit. "Delete and register again" is the answer, and the deletion states what
 * it takes with it.
 *
 * The **name** is optional, and leaving it empty is the better answer rather than the
 * lazy one: an unnamed row is named by the platform, in the current language, at every
 * read — so it follows the app's language instead of freezing in the one it was typed in.
 */
class CurrencyFormModal(
    private val existing: CurrencyInfo? = null,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<CurrencyFormViewModel> { parametersOf(existing) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(
                    if (uiState.isEditing) {
                        Res.string.currency_form_title_edit
                    } else {
                        Res.string.currency_form_title_new
                    }
                ),
                style = typography.headlineSmall,
                color = colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = uiState.code,
                onValueChange = { viewModel.onAction(CurrencyFormAction.ChangeCode(it)) },
                label = { Text(stringResource(Res.string.currency_form_code)) },
                supportingText = { Text(stringResource(Res.string.currency_form_code_helper)) },
                // Locked while editing, and shown rather than hidden: a locked field
                // still answers what the row is, which a hidden one does not.
                enabled = !uiState.isEditing,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.symbol,
                onValueChange = { viewModel.onAction(CurrencyFormAction.ChangeSymbol(it)) },
                label = { Text(stringResource(Res.string.currency_form_symbol)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.onAction(CurrencyFormAction.ChangeName(it)) },
                label = { Text(stringResource(Res.string.currency_form_name)) },
                supportingText = { Text(stringResource(Res.string.currency_form_name_helper)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringUiText(error),
                    color = colorScheme.error,
                    fontSize = 13.sp,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.onAction(CurrencyFormAction.Submit) },
                enabled = uiState.canSubmit,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.currency_form_save),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
