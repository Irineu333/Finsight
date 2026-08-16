package com.neoutils.finsight.mcp

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.mapper.AgentActivityMapper
import com.neoutils.finsight.database.repository.AgentActivityRepository
import com.neoutils.finsight.feature.mcp.api.IAgentActivityRepository
import com.neoutils.finsight.feature.mcp.api.McpPermissionAxis
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.time.Clock

/**
 * A real server over a real socket, with the preferences the test states and a database of its own.
 *
 * Nothing here is a stand-in for the thing under test: the controller is the one the desktop app
 * resolves, the transport is the one it binds, and the activity log is the one that writes rows.
 * What the harness supplies is only the surroundings — a port nobody else holds, a `Settings` that
 * is not the developer's own, and a database file that no other test reads.
 */
internal class McpServerHarness(
    /**
     * Shared between harnesses on purpose in the tests about what survives a restart: the same map
     * handed to a second controller is what "the app was opened again" means here.
     */
    val settings: MapSettings = MapSettings(),
    tools: List<McpTool> = emptyList(),
    clock: Clock = Clock.System,
    /**
     * What the user has granted, stated by the test rather than inherited.
     *
     * All four by default, because most of this suite is about something else — the token, the
     * perimeter, a family of tools — and a test that had to grant a capability before exercising it
     * would be saying the permission was the subject when it was not. `null` leaves whatever the
     * `Settings` already hold, which is how the tests about the *initial* state ask what an
     * untouched installation grants.
     */
    permissions: Set<McpPermissionAxis>? = McpPermissionAxis.entries.toSet(),
) : AutoCloseable {

    /**
     * A file rather than `:memory:`. In-memory databases are shared across builders in one JVM, and
     * a test that asserts the activity log is empty would be reading another test's rows.
     */
    private val file: File = File.createTempFile("finsight-mcp", ".db")
        .also { it.delete(); it.deleteOnExit() }

    val database: AppDatabase = Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    val activity: IAgentActivityRepository = AgentActivityRepository(
        dao = database.agentActivityDao(),
        mapper = AgentActivityMapper(),
        clock = clock,
    )

    /**
     * What the server offers. Read once per client session, so a test whose tool needs [database]
     * can add to it after the harness exists and before the server comes up.
     */
    val tools: MutableList<McpTool> = tools.toMutableList()

    private val serverSettings = McpServerSettings(settings).also { stored ->
        permissions?.let { granted ->
            McpPermissionAxis.entries.forEach { stored.setPermission(it, it in granted) }
        }
    }

    val controller = DesktopMcpServerController(
        settings = serverSettings,
        journal = AgentActivityJournal(activity),
        tools = this.tools,
    )

    override fun close() {
        database.close()
        file.delete()
    }

    companion object {

        /**
         * A port the operating system has just confirmed is free, on loopback.
         *
         * Never the app's own 8477: a suite that bound it would fail on a developer's machine with
         * the app open, and would be two tests fighting each other on any machine.
         */
        fun freePort(): Int = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
            .use { it.localPort }
    }
}
