package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.nav_dashboard
import com.neoutils.finsight.resources.nav_transactions
import com.neoutils.finsight.ui.theme.Primary1
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun BottomNavigationBar(
    selectedItem: NavigationItem,
    onItemSelected: (NavigationItem) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        NavigationItem.entries.forEach { item ->
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
                modifier = Modifier
                    .weight(1f)
                    .testTag(BottomNavTestTags.item(item))
            )
        }
    }
}

enum class NavigationItem(
    val icon: ImageVector,
    val labelRes: StringResource,
    val screenName: String,
) {
    Dashboard(Icons.Default.Dashboard, Res.string.nav_dashboard, "dashboard"),
    Transactions(Icons.Default.Receipt, Res.string.nav_transactions, "transactions")
}
