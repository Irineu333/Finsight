package com.neoutils.finsight.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.neoutils.finsight.util.CategoryIcon

class CategoryLazyIcon(
    val key: String,
) : LazyIcon {

    val icon = CategoryIcon.fromKey(key).icon

    @Composable
    override fun invoke(): Painter {
        return rememberVectorPainter(icon)
    }
}