@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)

package com.neoutils.finance.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import org.koin.compose.koinInject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

val LocalModalManager = compositionLocalOf<ModalManager> { error("No ModalManager provided") }
val LocalNavigator = compositionLocalOf<Navigator> { Navigator {} }

class Navigator(private val onNavigate: (NavigationAction) -> Unit) {
    fun navigate(action: NavigationAction) {
        onNavigate(action)
    }
}

sealed class NavigationAction {
    data class InvoiceTransactions(val creditCardId: Long) : NavigationAction()
    data class CreditCards(val creditCardId: Long) : NavigationAction()
    data class Accounts(val accountId: Long? = null) : NavigationAction()
}

class ModalManager {

    private var modalState = mutableStateListOf<Modal>()

    fun show(modal: Modal) {
        modalState.add(modal)
    }

    @Composable
    fun Content() {
        modalState.forEach { it.Content() }
    }

    fun dismiss() {
        modalState.removeAt(modalState.lastIndex)
    }

    fun dismissAll() {
        modalState.clear()
    }
}

@Composable
fun ModalManagerHost(
    onNavigate: (NavigationAction) -> Unit = {},
    content: @Composable () -> Unit
) {

    val modalManager = koinInject<ModalManager>()
    val navigator = Navigator(onNavigate)

    CompositionLocalProvider(
        LocalModalManager provides modalManager,
        LocalNavigator provides navigator,
    ) {
        content()
        modalManager.Content()
    }
}

abstract class Modal {

    val key = Uuid.random().toString()

    @Composable
    abstract fun Content()
}

abstract class ModalBottomSheet : Modal(), ViewModelStoreOwner {

    override val viewModelStore = ViewModelStore()

    private val providedValue get() = LocalViewModelStoreOwner provides this

    @Composable
    override fun Content() {

        val manager = LocalModalManager.current

        ModalBottomSheet(
            onDismissRequest = {
                manager.dismiss()
            },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            ),
            content = {

                CompositionLocalProvider(providedValue) {
                    BottomSheetContent()
                }

                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
            },
            contentWindowInsets = {
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
            }
        )
    }

    @Composable
    protected abstract fun ColumnScope.BottomSheetContent()
}