package com.neoutils.finance.ui.component

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
fun ModalManagerHost(content: @Composable (ModalManager) -> Unit) {

    val modalManager = remember { ModalManager() }

    CompositionLocalProvider(
        LocalModalManager provides modalManager,
    ) {
        content(modalManager)
        modalManager.Content()
    }
}

interface Modal {
    @Composable
    fun Content()
}