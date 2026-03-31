package com.neoutils.finsight.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

val LocalNavigationDispatcher = compositionLocalOf<NavigationDispatcher> {
    error("No NavigationDispatcher provided")
}

interface NavigationDispatcher {
    fun dispatch(destination: NavigationDestination)
}

sealed interface NavigationDestination {
    data object Categories : NavigationDestination
    data class InvoiceTransactions(val creditCardId: Long) : NavigationDestination
    data class CreditCards(val creditCardId: Long? = null) : NavigationDestination
    data class Accounts(val accountId: Long? = null) : NavigationDestination
    data object Installments : NavigationDestination
    data object Budgets : NavigationDestination
    data object Recurring : NavigationDestination
    data object ReportConfig : NavigationDestination
}

@Composable
fun NavigationDispatcherProvider(
    dispatcher: NavigationDispatcher,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalNavigationDispatcher provides dispatcher,
        content = content,
    )
}
