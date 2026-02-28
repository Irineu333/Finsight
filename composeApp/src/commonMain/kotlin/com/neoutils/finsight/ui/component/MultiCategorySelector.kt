@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.multi_category_selector_label
import com.neoutils.finsight.resources.multi_category_selector_none
import com.neoutils.finsight.resources.multi_category_selector_plural
import org.jetbrains.compose.resources.stringResource

@Composable
fun MultiCategorySelector(
    selectedCategories: List<Category>,
    categories: List<Category>,
    onCategoryToggled: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    val noneLabel = stringResource(Res.string.multi_category_selector_none)
    val pluralLabel = stringResource(Res.string.multi_category_selector_plural, selectedCategories.size)
    val displayText = when {
        selectedCategories.isEmpty() -> noneLabel
        selectedCategories.size == 1 -> selectedCategories.first().name
        else -> pluralLabel
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (categories.isNotEmpty()) {
                expanded = it
            }
        },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(text = stringResource(Res.string.multi_category_selector_label)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            enabled = categories.isNotEmpty(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            categories.forEach { category ->
                val isSelected = selectedCategories.any { it.id == category.id }
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryIconBox(
                                category = category,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(4.dp),
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                text = category.name,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    onClick = { onCategoryToggled(category) },
                )
            }
        }
    }
}
