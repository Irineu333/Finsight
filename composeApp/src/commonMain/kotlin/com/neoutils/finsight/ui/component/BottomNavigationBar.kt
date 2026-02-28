package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.ui.theme.Primary1
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.nav_dashboard
import com.neoutils.finsight.resources.nav_transactions
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun BottomNavigationBar(
    selectedItem: NavigationItem,
    onItemSelected: (NavigationItem) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth()
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
                    modifier = Modifier.weight(1f)
                )
            }
        }

        FloatingActionButton(
            onClick = onAddClick,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-28).dp)
                .size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

enum class NavigationItem(
    val icon: ImageVector,
    val labelRes: StringResource
) {
    Dashboard(Icons.Default.Dashboard, Res.string.nav_dashboard),
    Transactions(Icons.Default.Receipt, Res.string.nav_transactions)
}
