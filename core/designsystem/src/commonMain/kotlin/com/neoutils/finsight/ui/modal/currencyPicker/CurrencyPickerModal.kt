package com.neoutils.finsight.ui.modal.currencyPicker

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet

/**
 * One currency as the picker shows it.
 *
 * The name arrives already resolved to a string, and the list already built, for the
 * same reason `IconPickerModal` takes its icons: this module sees only `core/common`
 * and `core/resources`, and the curated catalog is the app's opinion about which
 * currencies it supports, which lives in `:core:model`. The picker renders a choice;
 * it does not decide what is on offer.
 */
data class CurrencyOption(
    val code: String,
    val symbol: String,
    val name: String,
)

/**
 * The shared currency picker — sibling of `IconPickerModal`, and here for the same
 * reason it is: a modal more than one feature opens.
 *
 * It offers what the caller hands it, which is always the curated catalog of
 * two-decimal currencies (design D14). System accounts are not currencies and can
 * never appear here.
 */
class CurrencyPickerModal(
    private val title: String,
    private val currencies: List<CurrencyOption>,
    private val selectedCode: String?,
    private val onCurrencySelected: (CurrencyOption) -> Unit,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val modalManager = LocalModalManager.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            // Left-aligned, like `IconPickerModal`. A form centres its title because it
            // is a dialog about one thing; a picker's title labels the list under it,
            // and labels sit above the left edge of what they label.
            Text(
                text = title,
                style = typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                items(currencies, key = { it.code }) { currency ->
                    CurrencyRow(
                        currency = currency,
                        isSelected = currency.code == selectedCode,
                        onClick = {
                            onCurrencySelected(currency)
                            modalManager.dismiss()
                        },
                    )
                }
            }
        }
    }
}

/**
 * One offered currency.
 *
 * The symbol sits in the same accent-tinted box every icon in this app sits in, and the
 * selected row carries the 2dp accent border `IconPickerModal` established — the two are
 * siblings, and a picker that renders its choices in `onSurfaceVariant` reads as a list
 * of things the app has disabled rather than a list of things to pick.
 *
 * The colour says nothing here either: it is the same accent on every row, and what
 * marks the selection is the border **and** the check.
 */
@Composable
private fun CurrencyRow(
    currency: CurrencyOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val accentColor = colorScheme.primary

    Surface(
        onClick = onClick,
        color = if (isSelected) {
            accentColor.copy(alpha = 0.12f)
        } else {
            colorScheme.surfaceContainerHighest
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            // Derived from the code, so an option is reached by the currency it offers and
            // not by its position in a list the user's own registry decides the order of.
            .testTag("currency_picker_option_${currency.code}")
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = accentColor,
                        shape = RoundedCornerShape(12.dp),
                    )
                } else {
                    Modifier
                }
            ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // The symbol as the glyph, in the same tinted box the account form gives
            // an icon.
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) {
                    accentColor.copy(alpha = 0.20f)
                } else {
                    accentColor.copy(alpha = 0.12f)
                },
                modifier = Modifier.size(40.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        text = currency.symbol,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currency.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = currency.code,
                    style = typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
