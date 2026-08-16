package com.neoutils.finsight

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.neoutils.finsight.di.appModules
import com.neoutils.finsight.extension.ProvideWindowScope
import com.neoutils.finsight.feature.mcp.api.McpServerController
import com.neoutils.finsight.firebase.DesktopFirebase
import com.neoutils.finsight.ui.App
import com.neoutils.finsight.window.WindowDefaults
import com.neoutils.finsight.window.WindowStatePersistence
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.core.context.startKoin
import java.awt.Dimension

@OptIn(FlowPreview::class)
fun main() = application {
    val koin = remember {
        DesktopFirebase.initialize()
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

    // **The MCP server has no life of its own**: this process is the whole of it, which is what
    // lets a write by an agent wake the `Flow` an open screen is collecting (design D1). It comes
    // up here if the user has already switched it on — the choice was made once and every later
    // launch honours it — and it opens nothing otherwise.
    //
    // Off the UI dispatcher, because binding a socket is not the window's work and because the
    // close below waits on the same lifecycle from the UI thread.
    val mcpServer = remember { koin.get<McpServerController>() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { mcpServer.start() }
    }

    Window(
        onCloseRequest = {
            // Awaited rather than fired off: the port has to be free before the process goes, or a
            // relaunch races the socket it just left behind. Closing the window is not the user
            // switching the server off, so nothing about their choice is touched here.
            runBlocking { mcpServer.stop() }
            exitApplication()
        },
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
