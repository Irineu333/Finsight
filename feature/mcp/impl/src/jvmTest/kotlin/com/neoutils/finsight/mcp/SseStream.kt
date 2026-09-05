package com.neoutils.finsight.mcp

import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * The channel a server speaks on its own initiative — the standalone `GET` event stream a client
 * holds open beside its requests.
 *
 * It exists because a notification is not a reply. `RawHttp` asks a question and reads the answer to
 * end of stream, which can prove nothing about `notifications/tools/list_changed`: that message is
 * sent when the *user* moves a switch, to whoever happens to be connected, on no request of theirs.
 * Reaching it takes a connection that is already open and stays open, which is this.
 */
internal class SseStream private constructor(
    private val socket: Socket,
    private val received: AtomicReference<String>,
) : AutoCloseable {

    /** Everything the server has pushed so far, frames and all. */
    val text: String get() = received.get()

    /**
     * Waits for the stream to carry [fragment], and answers what had arrived by then.
     *
     * A deadline rather than an assertion on what is already there: the notification travels while
     * the test is doing something else, and reading once would be reading a race.
     */
    fun await(fragment: String, timeoutMillis: Long = AWAIT_TIMEOUT_MILLIS): String {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val seen = received.get()
            if (fragment in seen) return seen
            Thread.sleep(POLL_MILLIS)
        }
        return received.get()
    }

    /** Answers what arrived in [millis], having waited the whole of it. Used to prove silence. */
    fun quietFor(millis: Long): String {
        Thread.sleep(millis)
        return received.get()
    }

    override fun close() {
        runCatching { socket.close() }
    }

    companion object {

        /**
         * Opens the stream for a session that is already initialised, and starts draining it.
         *
         * The drain runs on a thread of its own because the socket read blocks until the server
         * writes, and the point of the stream is to be waiting when nothing is being asked of it.
         */
        fun open(port: Int, token: String?, sessionId: String): SseStream {
            val socket = Socket().apply {
                connect(InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MILLIS)
                soTimeout = READ_TIMEOUT_MILLIS
            }

            val request = buildString {
                append("GET /mcp HTTP/1.1\r\n")
                append("Host: 127.0.0.1:$port\r\n")
                append("Accept: text/event-stream\r\n")
                token?.let { append("Authorization: Bearer $it\r\n") }
                append("mcp-session-id: $sessionId\r\n")
                append("\r\n")
            }

            socket.getOutputStream().apply {
                write(request.toByteArray(Charsets.US_ASCII))
                flush()
            }

            val received = AtomicReference("")

            thread(isDaemon = true, name = "mcp-sse-$port") {
                runCatching {
                    val reader = InputStreamReader(socket.getInputStream(), Charsets.UTF_8)
                    val chunk = CharArray(BUFFER_CHARS)
                    while (true) {
                        val read = try {
                            reader.read(chunk)
                        } catch (_: SocketTimeoutException) {
                            // A stream that has said nothing yet is still a stream.
                            continue
                        }
                        if (read < 0) break
                        received.updateAndGet { it + String(chunk, 0, read) }
                    }
                }
            }

            val stream = SseStream(socket, received)

            // Waits for the server's own opening frame before handing the stream back. Writing the
            // request does not establish it — the connection still has to be accepted, routed and
            // registered — and a test that flipped a switch before that would be asking whether a
            // notification reaches a stream that does not exist yet. The protocol drops one sent to
            // nobody, correctly, so the question would never be the one the test means to ask.
            check(OPENING_FRAME in stream.await(OPENING_FRAME)) {
                "the event stream never opened: ${stream.text}"
            }

            return stream
        }

        /** The empty event the transport writes to commit the headers, and nothing else does. */
        private const val OPENING_FRAME = "data:"

        private const val CONNECT_TIMEOUT_MILLIS = 2_000
        private const val READ_TIMEOUT_MILLIS = 500
        private const val AWAIT_TIMEOUT_MILLIS = 5_000L
        private const val POLL_MILLIS = 20L
        private const val BUFFER_CHARS = 4 * 1024
    }
}
