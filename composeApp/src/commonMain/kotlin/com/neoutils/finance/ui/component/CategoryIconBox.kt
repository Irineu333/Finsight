package com.neoutils.finance.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.neoutils.finance.data.Category
import com.neoutils.finance.ui.icons.CategoryIcon
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income

@Composable
fun CategoryIconBox(
    category: Category,
    shape: Shape,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = when (category.type) {
            Category.CategoryType.INCOME -> Income
            Category.CategoryType.EXPENSE -> Expense
        }.copy(alpha = 0.2f),
        shape = shape,
        modifier = modifier,
    ) {
        Icon(
            imageVector = CategoryIcon.fromKey(category.key).icon,
            contentDescription = null,
            tint = when (category.type) {
                Category.CategoryType.INCOME -> Income
                Category.CategoryType.EXPENSE -> Expense
            },
            modifier = Modifier.padding(contentPadding)
        )
    }
}
