package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.extension.currencyDisplayName
import com.neoutils.finsight.extension.currencySymbol
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.currency_selector_label
import com.neoutils.finsight.resources.currency_selector_locked
import org.jetbrains.compose.resources.stringResource

/**
 * The currency of an account or a card, in the form that creates it and in the form that
 * edits it.
 *
 * It reuses the shape of the default-account row whole — the 52dp box with a glyph, the title,
 * the alternating subtitle, and the same locking mechanics — with the currency's **symbol** in
 * the place of the icon. The consequence is deliberate: "currency locked" reads exactly like
 * "the default account cannot change", a significance this app's user has already learnt.
 *
 * **Always rendered**, and not revealed once the app holds a second currency (design D23). The
 * ~60dp it costs someone who will never touch it buys a form that does not change shape with
 * global state, and a currency that is an attribute of the account the way its icon is rather
 * than a feature to be discovered.
 *
 * By design D12 it has two behaviours, and they are decided by the **mode of the form** and
 * not by the state of the account: a selector on creation, a locked state line on edit, always.
 */
@Composable
fun CurrencySelectorRow(
    currency: String,
    canChange: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = if (canChange) {
        currencyDisplayName(currency)
    } else {
        stringResource(Res.string.currency_selector_locked)
    }

    Surface(
        onClick = { if (canChange) onClick() },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceContainerLow,
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
                color = colorScheme.primary.copy(alpha = if (canChange) 0.12f else 0.08f),
                modifier = Modifier.size(52.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = currencySymbol(currency),
                        style = typography.titleMedium,
                        color = if (canChange) colorScheme.primary else colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.currency_selector_label),
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "$currency · $subtitle",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // No chevron when locked — the same way the default-account row drops its
            // affordance rather than showing a dead one.
            if (canChange) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(20.dp),
                )
            }
        }
    }
}
