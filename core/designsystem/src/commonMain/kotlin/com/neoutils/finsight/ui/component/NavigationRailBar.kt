package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.neoutils.finsight.ui.theme.Primary1
import org.jetbrains.compose.resources.stringResource

@Composable
fun <T : BottomNavigationItem> NavigationRailBar(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        header = header?.let { headerContent -> { headerContent() } },
        modifier = modifier.fillMaxHeight()
    ) {
        items.forEach { item ->
            val label = stringResource(item.labelRes)
            NavigationRailItem(
                selected = selectedItem == item,
                onClick = { onItemSelected(item) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = label
                    )
                },
                label = { Text(label) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = Primary1,
                    selectedTextColor = Primary1,
                    indicatorColor = Primary1.copy(alpha = 0.1f)
                )
            )
        }
    }
}
