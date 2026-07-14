package com.neoutils.finsight

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.neoutils.finsight.di.appModules
import com.neoutils.finsight.extension.ProvideWindowScope
import com.neoutils.finsight.ui.App
import com.neoutils.finsight.window.WindowDefaults
import com.neoutils.finsight.window.WindowStatePersistence
import com.russhwolf.settings.Settings
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import org.koin.core.context.startKoin
import java.awt.Dimension

@OptIn(FlowPreview::class)
fun main() = application {
    val koin = remember {
        startKoin {
            modules(appModules)
        }.koin
    }

    val windowPersistence = remember { WindowStatePersistence(koin.get<Settings>()) }
    val initialState = remember { windowPersistence.load() }

    val state = rememberWindowState(
        size = initialState.size,
        position = initialState.position,
        placement = initialState.placement,
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        title = "Finsight",
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(
                WindowDefaults.MinSize.width.value.toInt(),
                WindowDefaults.MinSize.height.value.toInt(),
            )
        }

        LaunchedEffect(state) {
            snapshotFlow { Triple(state.size, state.position, state.placement) }
                .debounce(DEBOUNCE_MILLIS)
                .collect { (size, position, placement) ->
                    windowPersistence.save(size, position, placement)
                }
        }

        ProvideWindowScope {
            App()
        }
    }
}

private const val DEBOUNCE_MILLIS = 300L
