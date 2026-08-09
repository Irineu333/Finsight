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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.extension.LocalCurrencySymbols
import com.neoutils.finsight.ui.util.optionalTestTag

/**
 * The currency of an account or of a card, in a form — **always rendered**, design D23.
 *
 * The account form is the only door a second currency is ever born through, so this row
 * cannot hide itself while there is a single currency: if it did, there would never be a
 * second one. Cost accepted deliberately: ~60dp of form for a user who will never touch
 * it.
 *
 * It is the `DefaultAccountSelector` of the account form wearing different clothes — the
 * currency's **symbol as its glyph** instead of an icon, a chevron instead of a switch —
 * and it keeps that component's locking mechanic exactly: `primary` and touchable while
 * the currency can still change, `onSurfaceVariant` and chevron-less once it cannot. The
 * deliberate consequence is that "currency is locked" *reads like* "the default account
 * cannot change", a signifier this app's user has already learned.
 *
 * @param canChange decided by the **mode of the form** — a picker while creating, a
 * locked state row while editing — and never by the state of the account or card, so
 * there is no third case and no condition to keep correct (design D12).
 * @param subtitle what that state means, in the caller's own words: the two forms lock
 * for the same reason but say it about different things.
 */
@Composable
fun CurrencyRow(
    currency: String,
    label: String,
    subtitle: String,
    canChange: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Named on [label], which is the line stating the currency, and not on the row: the
    // row is a clickable `Surface` that publishes no text of its own, so a tag there is
    // found by an assertion and reads empty. Tapping still works through it — the touch
    // lands on the surface underneath.
    labelTestTag: String? = null,
) {
    val accentColor = MaterialTheme.colorScheme.primary

    Surface(
        onClick = { if (canChange) onClick() },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                    // The symbol *is* the icon: no glyph this app could draw says
                    // "dollars" as plainly as `US$` already does.
                    Text(
                        text = LocalCurrencySymbols.current(currency),
                        fontWeight = FontWeight.Medium,
                        color = if (canChange) {
                            accentColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = label,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.optionalTestTag(labelTestTag),
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

            if (canChange) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
