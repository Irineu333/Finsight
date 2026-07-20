@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)

package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.neoutils.finsight.util.UiText
import org.koin.compose.koinInject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

val LocalModalManager = compositionLocalOf<ModalManager> { error("No ModalManager provided") }

class ModalManager {

    private var modalState = mutableStateListOf<Modal>()

    /**
     * Surfaces why an action was refused, as a modal over the one that tried it.
     *
     * A modal action that fails used to record the exception and stop there: the
     * sheet simply did not close and the user was told nothing. Solving that per
     * modal meant plumbing in every ViewModel, and the ones that were missed stayed
     * silent — so it lives here, where every modal already is.
     */
    fun showError(message: UiText) {
        show(ErrorModal(message))
    }

    fun show(modal: Modal) {
        modalState.add(modal)
    }

    @Composable
    fun Content() {
        modalState.forEach { modal ->
            key(modal.key) {
                modal.Content()
            }
        }
    }

    fun dismiss() {
        modalState.lastOrNull()?.let(::dismiss)
    }

    fun dismiss(modal: Modal) {
        if (!modalState.remove(modal)) return
        modal.onDismissed()
    }

    fun dismissAll() {
        modalState.forEach(Modal::onDismissed)
        modalState.clear()
    }
}

@Composable
fun ModalManagerHost(
    content: @Composable () -> Unit
) {
    val modalManager = koinInject<ModalManager>()

    CompositionLocalProvider(
        LocalModalManager provides modalManager,
    ) {
        content()
        modalManager.Content()
    }
}

abstract class Modal {

    val key = Uuid.random().toString()

    open fun onDismissed() = Unit

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
                manager.dismiss(this@ModalBottomSheet)
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

    override fun onDismissed() {
        viewModelStore.clear()
    }

    @Composable
    protected abstract fun ColumnScope.BottomSheetContent()
}
