package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.model.displayColor
import com.neoutils.finsight.ui.icons.LazyIcon
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income

@Composable
fun CategoryIconBox(
    category: Category,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(12.dp),
    color: androidx.compose.ui.graphics.Color? = null,
) {
    val resolvedColor = color ?: category.displayColor

    CategoryIconBox(
        icon = category.icon,
        tint = resolvedColor,
        modifier = modifier,
        shape = shape,
        contentPadding = contentPadding,
    )
}

@Composable
fun CategoryIconBox(
    icon: LazyIcon,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(12.dp),
    /**
     * How present the box behind the icon is. The default is the identity colour of a
     * category; a neutral tint is dialled down, because there it stands for "secondary"
     * rather than for a colour worth recognising.
     */
    containerAlpha: Float = 0.2f,
) {
    Surface(
        color = tint.copy(alpha = containerAlpha),
        shape = shape,
        modifier = modifier,
    ) {
        Icon(
            painter = icon(),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.padding(contentPadding)
        )
    }
}
