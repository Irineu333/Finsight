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
import com.neoutils.finsight.extension.LocalCurrencySymbols
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.account_selector_label
import com.neoutils.finsight.ui.util.optionalTestTag
import org.jetbrains.compose.resources.stringResource

/**
 * Picks one of the user's accounts.
 *
 * When more than one currency is on offer, each name carries its currency's symbol —
 * `Chase · US$`. It appears only then, and it is derived rather than declared by the
 * caller: with a single currency the selector is exactly the one it always was, and
 * with two, "Chase" and "Nubank" stop being told apart by memory alone.
 *
 * @param currencyScope the set that question is asked of. It defaults to what is
 * rendered, and a caller widens it when the list it renders is a **slice** of a larger
 * one. A transfer is the case that proves it: its destination list is the user's
 * accounts minus the source, so with one account per currency the slice holds a single
 * currency and the symbol used to vanish from the destination — in the one operation
 * that exists because the two currencies differ. Which accounts are *offered* and
 * whether the user needs telling *which currency* are two questions, and only the first
 * is about the slice.
 */
@Composable
fun AccountSelector(
    selectedAccount: Account?,
    accounts: List<Account>,
    onAccountSelected: (Account?) -> Unit,
    label: String = "",
    currencyScope: List<Account> = accounts,
    modifier: Modifier = Modifier,
    valueTestTag: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    val showsCurrency = remember(currencyScope) {
        currencyScope.map { it.currency }.distinct().size > 1
    }

    val symbolOf = LocalCurrencySymbols.current

    fun Account.label() = if (showsCurrency) {
        "$name · ${symbolOf(currency)}"
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
                .optionalTestTag(valueTestTag)
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