package com.neoutils.finsight.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.extension.toMoneyFormat
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.category_spending_card_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun CategorySpendingCard(
    categorySpending: List<CategorySpending>,
    modifier: Modifier = Modifier,
    onCategoryClick: (Category) -> Unit = {}
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.category_spending_card_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            categorySpending.forEach { spending ->
                CategorySpendingItem(
                    spending = spending,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clickable(
                            onClick = {
                                onCategoryClick(spending.category)
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun CategorySpendingItem(
    spending: CategorySpending,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        CategoryIconBox(
            category = spending.category,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.aspectRatio(1f),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = spending.category.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )

                Text(
                    text = spending.amount.toMoneyFormat(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            LinearProgressIndicator(
                progress = { (spending.percentage / 100).toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = when (spending.category.type) {
                    Category.Type.INCOME -> Income
                    Category.Type.EXPENSE -> Expense
                },
                trackColor = colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
                gapSize = (-4).dp,
            )
        }
    }
}
