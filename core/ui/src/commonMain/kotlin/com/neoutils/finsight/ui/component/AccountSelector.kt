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
import com.neoutils.finsight.domain.model.CurrencyCatalog
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.account_selector_label
import org.jetbrains.compose.resources.stringResource

/**
 * Picks one of the user's accounts.
 *
 * When more than one currency is on offer, each name carries its currency's symbol —
 * `Chase · US$`. It appears only then, and it is derived from the list rather than
 * declared by the caller: with a single currency the selector is exactly the one it
 * always was, and with two, "Chase" and "Nubank" stop being told apart by memory alone.
 */
@Composable
fun AccountSelector(
    selectedAccount: Account?,
    accounts: List<Account>,
    onAccountSelected: (Account?) -> Unit,
    label: String = "",
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    val showsCurrency = remember(accounts) {
        accounts.map { it.currency }.distinct().size > 1
    }

    fun Account.label() = if (showsCurrency) {
        "$name · ${CurrencyCatalog.symbolOf(currency)}"
    } else {
        name
    }

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
            value = selectedAccount?.label() ?: "",
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
                            text = account.label(),
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