package com.neoutils.finsight.ui.screen.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.ui.graphics.vector.ImageVector
import com.neoutils.finsight.feature.dashboard.api.DashboardRoute
import com.neoutils.finsight.feature.transactions.api.TransactionsRoute
import com.neoutils.finsight.navigation.NavRoute
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.nav_dashboard
import com.neoutils.finsight.resources.nav_transactions
import com.neoutils.finsight.ui.component.BottomNavigationItem
import org.jetbrains.compose.resources.StringResource

internal enum class NavigationItem(
    override val icon: ImageVector,
    override val labelRes: StringResource,
    val screenName: String,
    val route: NavRoute,
) : BottomNavigationItem {
    Dashboard(Icons.Default.Dashboard, Res.string.nav_dashboard, "dashboard", DashboardRoute),
    Transactions(Icons.Default.Receipt, Res.string.nav_transactions, "transactions", TransactionsRoute()),
}
