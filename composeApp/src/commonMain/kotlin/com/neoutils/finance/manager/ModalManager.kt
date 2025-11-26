package com.neoutils.finance.manager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

val LocalModalManager = compositionLocalOf<ModalManager> { error("No ModalManager provided") }

class ModalManager {

    private var modalState by mutableStateOf<Modal?>(null)

    fun show(modal: Modal) {
        modalState = modal
    }

    @Composable
    fun Content() {
        CompositionLocalProvider(
            LocalModalManager provides this
        ) {
            modalState?.Content()
        }
    }

    fun dismiss() {
        modalState = null
    }
}

interface Modal {
    @Composable
    fun Content()
}