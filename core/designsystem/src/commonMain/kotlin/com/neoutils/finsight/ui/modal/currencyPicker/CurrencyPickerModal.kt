package com.neoutils.finsight.ui.modal.currencyPicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.extension.currencyDisplayName
import com.neoutils.finsight.extension.currencySymbol
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet

/**
 * Picking a currency — sibling of the icon picker, and here for the same reason: it is a
 * modal more than one feature opens, and `:core:designsystem` is where those live.
 *
 * The list is a **parameter**, exactly as the icon picker's is. Which currencies the app
 * offers is a product decision that belongs to the model layer, and no `:core:designsystem`
 * component reaches up for it — the caller that knows the catalog hands it over. That also
 * lets a caller offer a *narrower* set than the catalog: the budget form offers only the
 * currencies the user's accounts are actually in.
 *
 * Each row says three things — the code, the glyph and the name in the reader's language —
 * because none of them alone identifies a currency to everyone: the code is unambiguous and
 * opaque, the glyph is familiar and shared (`$` names half a dozen), the name is plain and
 * long.
 */
class CurrencyPickerModal(
    private val title: String,
    private val currencies: List<String>,
    private val selected: String?,
    private val onCurrencySelected: (String) -> Unit,
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
            Text(
                text = title,
                style = typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    // The catalog is long enough to scroll and short enough that a fixed
                    // height would leave a gap for a caller offering two.
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(currencies, key = { it }) { currency ->
                    CurrencyRow(
                        currency = currency,
                        isSelected = currency == selected,
                        onClick = {
                            onCurrencySelected(currency)
                            modalManager.dismiss()
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun CurrencyRow(
        currency: String,
        isSelected: Boolean,
        onClick: () -> Unit,
    ) {
        Surface(
            onClick = onClick,
            color = colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(36.dp),
                ) {
                    Text(
                        text = currencySymbol(currency),
                        style = typography.titleMedium,
                        color = if (isSelected) colorScheme.primary else colorScheme.onSurface,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currency,
                        style = typography.bodyLarge,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = currencyDisplayName(currency),
                        style = typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
