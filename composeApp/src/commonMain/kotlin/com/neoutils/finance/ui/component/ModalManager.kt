@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finance.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

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
}

@Composable
fun ModalManagerHost(content: @Composable () -> Unit) {

    val modalManager = remember { ModalManager() }

    CompositionLocalProvider(
        LocalModalManager provides modalManager,
    ) {
        content()
        modalManager.Content()
    }
}

interface Modal {

    val key: String
        get() = ""

    @Composable
    fun Content()
}

interface ModalBottomSheet : Modal {
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
    fun ColumnScope.BottomSheetContent()
}