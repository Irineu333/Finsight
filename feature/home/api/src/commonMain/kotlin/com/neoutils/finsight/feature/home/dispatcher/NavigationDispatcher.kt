package com.neoutils.finsight.feature.home.dispatcher

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

@Composable
fun rememberNavigationDispatcher(): NavigationDispatcher = remember { NavigationDispatcher() }

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
