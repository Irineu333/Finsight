@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.extension.currencySymbol
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.account_selector_label
import org.jetbrains.compose.resources.stringResource

@Composable
fun AccountSelector(
    selectedAccount: Account?,
    accounts: List<Account>,
    onAccountSelected: (Account?) -> Unit,
    label: String = "",
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    // Told apart by currency only where more than one is on offer: a list in a single
    // currency has nothing to disambiguate, and the suffix would be noise on every row.
    val currencySuffix = rememberCurrencySuffix(accounts.map { it.currency })

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (accounts.isNotEmpty()) {
                expanded = it
            }
        },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedAccount?.let { it.name + currencySuffix(it.currency) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = {
                Text(text = label.ifEmpty { stringResource(Res.string.account_selector_label) })
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            enabled = accounts.isNotEmpty(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = account.name + currencySuffix(account.currency),
                            fontSize = 14.sp
                        )
                    },
                    onClick = {
                        onAccountSelected(account)
                        expanded = false
                    }
                )
            }
        }
    }
}
/**
 * `· US$` after a name, and only when the list it comes from holds more than one currency.
 *
 * Derived from the offered list rather than from the app's whole set on purpose: what a
 * selector has to disambiguate is what it is showing. A list narrowed to one currency — the
 * recurring confirmation's, for instance — says so in its own words instead.
 */
@Composable
internal fun rememberCurrencySuffix(currencies: List<String>): (String) -> String {
    val isMixed = remember(currencies) { currencies.distinct().size > 1 }
    return remember(isMixed) {
        { currency -> if (isMixed) " · " + currencySymbol(currency) else "" }
    }
}
