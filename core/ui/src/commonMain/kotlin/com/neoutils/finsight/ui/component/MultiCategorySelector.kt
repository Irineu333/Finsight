@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.multi_category_selector_and
import com.neoutils.finsight.resources.multi_category_selector_done
import com.neoutils.finsight.resources.multi_category_selector_label
import com.neoutils.finsight.resources.multi_category_selector_none
import com.neoutils.finsight.ui.util.exposeTestTags
import com.neoutils.finsight.ui.util.optionalTestTag
import org.jetbrains.compose.resources.stringResource

@Composable
fun MultiCategorySelector(
    selectedCategories: List<Category>,
    categories: List<Category>,
    onCategoryToggled: (Category) -> Unit,
    modifier: Modifier = Modifier,
    valueTestTag: String? = null,
    onEmpty: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    val noneLabel = stringResource(Res.string.multi_category_selector_none)
    val andLabel = stringResource(Res.string.multi_category_selector_and)
    val displayText = when {
        selectedCategories.isEmpty() -> noneLabel
        else -> selectedCategories.formatForDisplay(andLabel)
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
                if (categories.isEmpty() && onEmpty != null) {
                    IconButton(onClick = onEmpty) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            enabled = categories.isNotEmpty() || onEmpty != null,
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .optionalTestTag(valueTestTag),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // A menu opens its own window, so the app window's opt-in does not reach it: without
            // this, the command below has a test tag that no driver can see on Android.
            modifier = Modifier.exposeTestTags(),
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

            // A multi-select menu does not close when something is picked, so it has to say how it
            // *is* closed. Tapping outside works and always did, but nothing on screen offered it,
            // and on iOS it is not even reachable: while a menu is up the system hides the rest of
            // the accessibility tree, so "outside" is a place with no name.
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(Res.string.multi_category_selector_done),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = { expanded = false },
                modifier = Modifier.testTag("multi_category_selector_done"),
            )
        }
    }
}

private fun List<Category>.formatForDisplay(andLabel: String): String {
    val names = map(Category::name)
    return when (names.size) {
        0 -> ""
        1 -> names.first()
        2 -> names.joinToString(separator = " $andLabel ")
        else -> names.dropLast(1).joinToString(", ") + " $andLabel " + names.last()
    }
}
