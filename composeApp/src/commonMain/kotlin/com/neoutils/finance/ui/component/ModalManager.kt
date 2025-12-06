@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)

package com.neoutils.finance.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import org.koin.compose.koinInject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

val LocalModalManager = compositionLocalOf<ModalManager> { error("No ModalManager provided") }

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
fun ModalManagerHost(content: @Composable () -> Unit) {

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

    @Composable
    abstract fun Content()
}

abstract class ModalBottomSheet : Modal() {
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
            content = { BottomSheetContent() }
        )
    }

    @Composable
    abstract fun ColumnScope.BottomSheetContent()
}