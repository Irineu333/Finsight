package com.neoutils.finsight.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter

/**
 * A [LazyIcon] over a vector the caller already holds — the glyph of something that is
 * not a category, and so has no key to be resolved from.
 *
 * It exists so that such a glyph is passed as an implementation of the interface rather
 * than as a lambda converted to it. [LazyIcon.invoke] is `@Composable`, and the Compose
 * plugin rewrites its signature; a SAM-converted lambda keeps naming the untransformed
 * one, which is in no binary. On Kotlin/Native that is not a compile error — the
 * reference resolves to nothing at link time and throws `IrLinkageError` the first time
 * the icon is composed.
 */
class VectorLazyIcon(
    val icon: ImageVector,
) : LazyIcon {

    @Composable
    override fun invoke(): Painter {
        return rememberVectorPainter(icon)
    }
}
