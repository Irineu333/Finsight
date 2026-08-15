package com.neoutils.finsight

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.neoutils.finsight.di.appModules
import com.neoutils.finsight.extension.ProvideWindowScope
import com.neoutils.finsight.firebase.DesktopFirebase
import com.neoutils.finsight.mcp.McpServerController
import com.neoutils.finsight.ui.App
import com.neoutils.finsight.window.WindowDefaults
import com.neoutils.finsight.window.WindowStatePersistence
import com.russhwolf.settings.Settings
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import java.awt.Dimension
import kotlin.system.exitProcess

@OptIn(FlowPreview::class)
fun main() {
    // Before Firebase, before Koin, before anything that could reach the database: the second
    // instance has to die while it is still a process that opened nothing.
    val guard = SingleInstanceGuard()
    when (val outcome = guard.tryAcquire()) {
        SingleInstanceGuard.Outcome.Acquired -> Unit
        is SingleInstanceGuard.Outcome.Refused -> {
            System.err.println("Finsight will not start: ${outcome.reason}.")
            exitProcess(EXIT_NOT_THE_OWNER)
        }
    }

    DesktopFirebase.initialize()

    val koin = startKoin {
        modules(appModules)
    }.koin

    // The server's lifetime is the process's, not the composition's: it is started here and
    // stopped on the way out, it reads no window state, and nothing of the app's interface takes
    // part in it.
    val mcpServer = koin.get<McpServerController>()
    runBlocking { mcpServer.start() }

    val windowPersistence = WindowStatePersistence(koin.get<Settings>())
    val initialState = windowPersistence.load()

    application {
        val state = rememberWindowState(
            size = initialState.size,
            position = initialState.position,
            placement = initialState.placement,
        )

        Window(
            onCloseRequest = {
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

    guard.release()
}

private const val DEBOUNCE_MILLIS = 300L

private const val EXIT_NOT_THE_OWNER = 1
