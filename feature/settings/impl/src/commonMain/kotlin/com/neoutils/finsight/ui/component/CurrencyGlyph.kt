package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The symbol as the glyph, in the box every leading icon of this app already lives in —
 * accent at 12%, 12dp corners — and at the size the account form established for one.
 *
 * The colour here says nothing: it is the same accent whatever the currency, and every
 * fact around it is carried by a word beside it. That is what separates it from the
 * `Warning` on an outdated rate, which is a signal and therefore never travels without
 * its label.
 *
 * It is shared between settings and the rate archive because the two screens are one
 * subject seen twice, and a currency that led with a glyph in one and with nothing in
 * the other is how the archive came to read like a log.
 */
@Composable
fun CurrencyGlyph(
    symbol: String,
    modifier: Modifier = Modifier,
    size: Int = 48,
) {
    GlyphBox(size = size, modifier = modifier) {
        Text(
            text = symbol,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.primary,
        )
    }
}

/** The same box as [CurrencyGlyph], holding an icon instead of a symbol. */
@Composable
fun CurrencyGlyphIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Int = 48,
) {
    GlyphBox(size = size, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colorScheme.primary,
        )
    }
}

@Composable
private fun GlyphBox(
    size: Int,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.primary.copy(alpha = 0.12f),
        modifier = modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
