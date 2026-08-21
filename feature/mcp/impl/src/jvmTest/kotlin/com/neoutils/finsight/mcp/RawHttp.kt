package com.neoutils.finsight.mcp

import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * An HTTP client made of a socket and a string, used by the tests that are about the perimeter.
 *
 * A client library is the wrong instrument for those: every one of them owns the `Host` header,
 * normalises what it sends and refuses to be told otherwise — and `Host` and `Origin` are exactly
 * what the defence under test reads. What goes on the wire here is what the test wrote, byte for
 * byte, which is the only way to stand in for a hostile page that writes whatever it likes.
 */
internal object RawHttp {

    /** Everything the server said back, unparsed apart from the status line and the headers. */
    data class Response(
        val status: Int,
        val headers: Map<String, String>,
        val body: String,
    ) {
        operator fun get(header: String): String? = headers[header.lowercase()]
    }

    @Suppress("LongParameterList")
    fun post(
        port: Int,
        body: String,
        path: String = "/mcp",
        address: String = "127.0.0.1",
        host: String = "127.0.0.1:$port",
        token: String? = null,
        origin: String? = null,
        sessionId: String? = null,
        accept: String = "application/json, text/event-stream",
    ): Response {
        val payload = body.toByteArray(Charsets.UTF_8)
        val request = buildString {
            append("POST $path HTTP/1.1\r\n")
            append("Host: $host\r\n")
            append("Accept: $accept\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${payload.size}\r\n")
            // Read to end-of-stream instead of decoding a length or a chunked body: the tests here
            // ask what the server answered, never how it framed it.
            append("Connection: close\r\n")
            token?.let { append("Authorization: Bearer $it\r\n") }
            origin?.let { append("Origin: $it\r\n") }
            sessionId?.let { append("mcp-session-id: $it\r\n") }
            append("\r\n")
        }

        Socket().use { socket ->
            socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MILLIS)
            socket.soTimeout = READ_TIMEOUT_MILLIS
            socket.getOutputStream().apply {
                write(request.toByteArray(Charsets.US_ASCII))
                write(payload)
                flush()
            }
            return parse(readAll(socket))
        }
    }

    private fun readAll(socket: Socket): String {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(BUFFER_BYTES)
        try {
            while (true) {
                val read = socket.getInputStream().read(chunk)
                if (read < 0) break
                buffer.write(chunk, 0, read)
            }
        } catch (_: SocketTimeoutException) {
            // A server that answered and kept the connection open has still answered.
        }
        return buffer.toString(Charsets.UTF_8)
    }

    private fun parse(raw: String): Response {
        val separator = raw.indexOf("\r\n\r\n")
        val head = if (separator < 0) raw else raw.substring(0, separator)
        val body = if (separator < 0) "" else raw.substring(separator + 4)
        val lines = head.split("\r\n")
        val status = lines.first().split(" ").getOrNull(1)?.toIntOrNull()
            ?: error("Not an HTTP response: $raw")
        val headers = lines.drop(1)
            .mapNotNull { line ->
                val colon = line.indexOf(':')
                if (colon <= 0) null else line.substring(0, colon).trim().lowercase() to
                    line.substring(colon + 1).trim()
            }
            .toMap()
        return Response(status = status, headers = headers, body = body)
    }

    private const val CONNECT_TIMEOUT_MILLIS = 2_000
    private const val READ_TIMEOUT_MILLIS = 5_000
    private const val BUFFER_BYTES = 8 * 1024
}
