package com.neoutils.finsight.feature.mcp.api

/**
 * The app answering an agent over the standard streams of the process it was launched in.
 *
 * It is the second shape the same surface takes. [McpServerController] is the one the window holds:
 * a socket, a port and a token, alive for as long as a window is. This one has no address and
 * nothing to configure — a client launches the installed executable, speaks the protocol down the
 * pipe it opened, and closing that pipe ends the session and the process with it. Which is why it
 * answers with the window closed: there is no residency to arrange, only a process that lives for
 * one conversation.
 *
 * **Serving takes no streams, deliberately.** A process has one pair of standard streams and
 * therefore one such session, so a stream on the signature would be a choice with one correct
 * answer — and stating it here would put a platform's file handles in a contract every target has to
 * be able to read. Which stream carries the protocol is settled before this exists, by the entry
 * point that claims the process's own output for it, and the implementation reads it from there.
 *
 * Only the desktop target has a process a client can launch. On the others this resolves to an
 * implementation that serves nothing, for the same reason the controller there opens no socket.
 *
 * **Nothing here widens what an agent may do.** The switch, the permission axes and the activity log
 * are the app's side of the wire in this mode exactly as in the other one; a session that could
 * change them would be a client granting itself.
 */
interface McpStdioSession {

    /**
     * Speaks the protocol on the process's standard streams until the client closes the input, and
     * returns once it has.
     *
     * It suspends for the whole life of the conversation, which is the life of the process: the
     * caller's next instruction after this is the shutdown. Returning is the client having gone,
     * never a failure — there is nothing to reconnect to and nothing to retry.
     */
    suspend fun serve()
}
