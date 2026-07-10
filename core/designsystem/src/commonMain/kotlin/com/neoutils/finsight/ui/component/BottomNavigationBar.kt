package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.ui.theme.Primary1
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

interface BottomNavigationItem {
    val icon: ImageVector
    val labelRes: StringResource
}

@Composable
fun <T : BottomNavigationItem> BottomNavigationBar(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        items.forEach { item ->
            val label = stringResource(item.labelRes)
            NavigationBarItem(
                selected = selectedItem == item,
                onClick = { onItemSelected(item) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = label
                    )
                },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Primary1,
                    selectedTextColor = Primary1,
                    indicatorColor = Primary1.copy(alpha = 0.1f)
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
