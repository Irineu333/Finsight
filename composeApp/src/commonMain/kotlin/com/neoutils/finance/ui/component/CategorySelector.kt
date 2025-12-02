@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finance.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import com.neoutils.finance.usecase.GetCategoriesUseCase
import org.koin.compose.koinInject

@Composable
fun CategorySelector(
    selectedCategory: Category?,
    categoryType: Category.CategoryType,
    onCategorySelected: (Category?) -> Unit,
    modifier: Modifier = Modifier,
    getCategoriesUseCase: GetCategoriesUseCase = koinInject()
) {
    val categories by getCategoriesUseCase(categoryType)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var expanded by remember { mutableStateOf(false) }
    val categoryColor = if (categoryType.isIncome) Income else Expense

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { 
            if (categories.isNotEmpty()) {
                expanded = it
            }
        },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedCategory?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = {
                Text(text = "Categoria")
            },
            leadingIcon = selectedCategory?.let {
                {
                    CategoryIconBox(
                        category = it,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(4.dp),
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            enabled = categories.isNotEmpty(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryIconBox(
                                category = category,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(4.dp),
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                text = category.name,
                                fontSize = 14.sp
                            )
                        }
                    },
                    onClick = {
                        onCategorySelected(category)
                        expanded = false
                    }
                )
            }
        }
    }
}
