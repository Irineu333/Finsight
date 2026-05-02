package com.neoutils.finsight.feature.home.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

val LocalNavigationDispatcher = compositionLocalOf<NavigationDispatcher> {
    error("No NavigationDispatcher provided")
}

class NavigationDispatcher {

    private val _events = Channel<NavigationDestination>(Channel.BUFFERED)

    val events: Flow<NavigationDestination> = _events.receiveAsFlow()

    fun dispatch(destination: NavigationDestination) {
        _events.trySend(destination)
    }
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
    data object Support : NavigationDestination
}

@Composable
fun rememberNavigationDispatcher(): NavigationDispatcher = remember { NavigationDispatcher() }

@Composable
fun NavigationDispatcherProvider(
    dispatcher: NavigationDispatcher = rememberNavigationDispatcher(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalNavigationDispatcher provides dispatcher,
        content = content,
    )
}
