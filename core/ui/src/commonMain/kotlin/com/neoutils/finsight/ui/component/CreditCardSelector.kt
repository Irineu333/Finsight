@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.extension.LocalCurrencySymbols
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.credit_card_selector_label
import com.neoutils.finsight.ui.util.optionalTestTag
import com.neoutils.finsight.util.AppIcon
import org.jetbrains.compose.resources.stringResource

/**
 * Picks one of the user's cards.
 *
 * Like [AccountSelector], each name carries its currency's symbol — `Chase · US$` —
 * only when more than one currency is on offer, derived from the list rather than
 * declared by a caller. A card states its currency because its `LIABILITY` account
 * does, hydrated on read; a card with none did not come from such a read, and is left
 * unmarked rather than denominated by a guess.
 */
@Composable
fun CreditCardSelector(
    creditCards: List<CreditCard>,
    creditCard: CreditCard?,
    onCreditCardSelected: (CreditCard) -> Unit,
    modifier: Modifier = Modifier,
    valueTestTag: String? = null,
    onEmpty: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    val showsCurrency = remember(creditCards) {
        creditCards.mapNotNull { it.currency }.distinct().size > 1
    }

    val symbolOf = LocalCurrencySymbols.current

    fun CreditCard.label() = currency
        ?.takeIf { showsCurrency }
        ?.let { "$name · ${symbolOf(it)}" }
        ?: name

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (creditCards.isNotEmpty()) {
                expanded = it
            }
        },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = creditCard?.label().orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = {
                Text(text = stringResource(Res.string.credit_card_selector_label))
            },
            leadingIcon = creditCard?.let {
                {
                    Icon(
                        imageVector = AppIcon.fromKey(it.iconKey).icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            trailingIcon = {
                if (creditCards.isEmpty() && onEmpty != null) {
                    IconButton(onClick = onEmpty) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            enabled = creditCards.isNotEmpty() || onEmpty != null,
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .optionalTestTag(valueTestTag)
                .animateContentSize()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            creditCards.forEach { creditCard ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = AppIcon.fromKey(creditCard.iconKey).icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = creditCard.label(),
                                fontSize = 14.sp
                            )
                        }
                    },
                    onClick = {
                        onCreditCardSelected(creditCard)
                        expanded = false
                    }
                )
            }
        }
    }
}
