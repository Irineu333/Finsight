package com.neoutils.finsight.feature.mcp.api

import java.io.OutputStream

/**
 * The process's standard output, taken for the protocol and given to nothing else.
 *
 * In the stdio mode `stdout` is the wire. A single `println` from anywhere — a library reporting a
 * missing configuration file, a driver announcing itself — lands in the middle of a JSON-RPC frame
 * and the client's parser never recovers, with no error anywhere saying why. So the stream is taken
 * away from the process at the first opportunity: what was `System.out` is kept here for the
 * transport, and `System.out` is pointed at `System.err`, which is the channel clients display and
 * where diagnostics belong (design D6).
 *
 * **The order is the whole of it, and it is the caller's to keep.** [claim] is the first instruction
 * of the `--mcp` entry point, before the object graph is built and therefore before anything the
 * graph does can print; [protocol] is read afterwards, by the session, and refuses to answer if the
 * claim was never made — a session writing the protocol to whatever `System.out` happens to be by
 * then is the defect this exists to prevent, and it would be silent.
 *
 * It lives in this module, rather than beside the session that reads it, because the two callers
 * are on opposite sides of the app: the entry point that claims the stream sees only a feature's
 * `api`, and the implementation that writes to it is an `impl` the entry point cannot name.
 */
object McpStdout {

    @Volatile
    private var claimed: OutputStream? = null

    /**
     * Takes the process's current standard output for the protocol and puts `System.out` on
     * `System.err` in its place.
     *
     * Claiming twice claims nothing the second time: the stream kept is the one that was standing
     * before anything of this app ran, and replacing it with the `System.err` the first call
     * installed would hand the protocol to the diagnostics channel.
     */
    @Synchronized
    fun claim() {
        if (claimed != null) return
        claimed = System.out
        System.setOut(System.err)
    }

    /**
     * The stream the protocol is written on — the standard output as it was before [claim] moved
     * everything else off it.
     *
     * Never `System.out`: by the time this is read, that is the diagnostics stream.
     */
    val protocol: OutputStream
        get() = checkNotNull(claimed) {
            "the protocol stream was read before it was claimed: call McpStdout.claim() as the " +
                "first instruction of the stdio entry point"
        }
}
