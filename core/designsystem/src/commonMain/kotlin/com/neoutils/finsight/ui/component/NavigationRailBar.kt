package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    // The header is rendered inside our own column (not the NavigationRail `header` slot) so we fully
    // control its vertical padding — the slot injects an extra fixed gap below the header that makes
    // the FAB look bottom-heavy. The items scroll while the header stays pinned.
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxHeight()
    ) {
        if (header != null) {
            Column(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                header()
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
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
}
