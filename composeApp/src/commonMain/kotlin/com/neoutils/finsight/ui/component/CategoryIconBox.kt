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
import com.neoutils.finsight.util.CategoryIcon
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
    val resolvedColor = color ?: when (category.type) {
        Category.Type.INCOME -> Income
        Category.Type.EXPENSE -> Expense
    }

    Surface(
        color = resolvedColor.copy(alpha = 0.2f),
        shape = shape,
        modifier = modifier,
    ) {
        Icon(
            painter = category.icon(),
            contentDescription = null,
            tint = resolvedColor,
            modifier = Modifier.padding(contentPadding)
        )
    }
}
