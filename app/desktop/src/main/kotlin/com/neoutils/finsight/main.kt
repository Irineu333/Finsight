package com.neoutils.finsight

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.neoutils.finsight.database.DatabaseOwnership
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
    // Before the graph, because the graph is what opens the database and the ownership is the
    // claim to be the process that does (design D4). A headless `--mcp` process takes it a
    // call at a time, so the wait here is a call in flight finishing; it is bounded, and a
    // limit that expires opens the window anyway — an app that refuses to start over a lock
    // would be a worse answer than a `Flow` that misses one update.
    val ownership = remember { DatabaseOwnership().acquire(DatabaseOwnership.WAIT_LIMIT) }

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

    // **The server the window carries is the one an open screen can hear**: it runs in this
    // process, so a write an agent makes through it wakes the `Flow` a screen is collecting. The
    // same surface is also served headless, by this executable under `--mcp`, and that process
    // has no screen to wake — which is what the ownership above is for: while the window holds
    // it, the headless one forwards here instead of writing on its own (design D3). The server
    // comes up if the user has already switched it on — the choice was made once and every later
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
            // The ownership lasts exactly as long as the window, and is given back rather than
            // left to the kernel to reclaim: the process exiting would drop it anyway, and
            // saying so here is what makes the lifetime a decision instead of a side effect.
            ownership?.release()
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
