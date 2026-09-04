package com.neoutils.finsight

import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.di.appModules
import com.neoutils.finsight.feature.mcp.api.McpStdioSession
import com.neoutils.finsight.feature.mcp.api.McpStdout
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * The app as a program a client runs: one conversation on the standard streams, and then nothing.
 *
 * **What is missing here is the point of it.** There is no `application {}`, so no toolkit is
 * started and no window is composed; there is no `DesktopFirebase.initialize()`, because the only
 * consumer of Firebase in this app is a screen and this process has none. What is left is the same
 * Koin aggregate the window builds — the same database, the same use cases, the same tools —
 * because a second aggregate kept in step by hand would be the thing that eventually answers
 * differently from the window (design D5).
 *
 * **The ownership of the database is deliberately not taken here.** It is taken a call at a time,
 * by the session, and given straight back: a headless process that claimed it at start-up would
 * hold it for the whole conversation, and the window the user then opened would wait on it and
 * open without it (design D3, D4).
 */
internal fun mcpStdioMain() {
    // First, and alone on this line because there is nothing that may precede it: from here on
    // `System.out` is the diagnostics stream, and the stream the protocol will be written on is
    // the one that was standing before anything of this app could print to it (design D6).
    McpStdout.claim()

    val koin = startKoin { modules(appModules) }.koin
    val session = koin.get<McpStdioSession>()

    try {
        // The conversation is the whole life of the process: this returns when the client closes
        // the input, and the next instruction is the shutdown.
        runBlocking { session.serve() }
    } finally {
        // Closing is asked for by hand because nothing else asks: the database is a Koin `single`
        // with no closing callback on it, and `stopKoin` drops references without touching what
        // they hold. Leaving it open would end the process with a write-ahead log for the next
        // launch to recover instead of one this run checkpointed. Resolving it here builds
        // nothing — the session above already pulled it in through the tools.
        koin.get<AppDatabase>().close()
        stopKoin()
    }
}
