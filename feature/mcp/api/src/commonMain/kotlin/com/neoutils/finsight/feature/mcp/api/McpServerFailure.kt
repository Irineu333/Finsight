package com.neoutils.finsight.feature.mcp.api

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.mcp_port_error_in_use
import com.neoutils.finsight.resources.mcp_port_error_unavailable
import com.neoutils.finsight.resources.mcp_server_error_port_in_use
import com.neoutils.finsight.resources.mcp_server_error_unavailable
import com.neoutils.finsight.util.UiText

/**
 * Why a server the user switched on is not listening.
 *
 * The set is small on purpose: only one of these is something the user can do anything about, and
 * the other exists so that a bind failing for any other reason is still said out loud instead of
 * being rounded down to "stopped".
 */
enum class McpServerFailure(val message: String) {

    /**
     * Another program already holds the configured port.
     *
     * The server does **not** move to a free one. A client configured for a port finds nothing
     * when the server quietly comes up on another, and the symptom — "the agent will not
     * connect" — then points nowhere near the cause (design D10).
     */
    PORT_IN_USE(message = "The configured port is already held by another program"),

    /** The loopback interface refused the bind for a reason that is not the port being taken. */
    UNAVAILABLE(message = "The loopback interface could not be bound"),
}

/**
 * The failure in the user's words, naming the port, because a port is the only thing here they can
 * act on — and naming the way out, because whoever reads this is anywhere in the app.
 */
fun McpServerState.Failed.toUiText(): UiText = when (cause) {
    McpServerFailure.PORT_IN_USE -> UiText.ResWithArgs(Res.string.mcp_server_error_port_in_use, port)
    McpServerFailure.UNAVAILABLE -> UiText.ResWithArgs(Res.string.mcp_server_error_unavailable, port)
}

/**
 * The same failure said to someone already standing at the port field.
 *
 * It is the same fact and a different sentence, because the audience is different: [toUiText] has
 * to send the reader somewhere, and this one is read *at* the place it would send them, where
 * "pick another port in the server settings" would point at the field it is written under. The
 * error belongs on the field and not in a notice of its own, so that the port and its refusal are
 * one thing on screen.
 */
fun McpServerState.Failed.toPortFieldUiText(): UiText = when (cause) {
    McpServerFailure.PORT_IN_USE -> UiText.ResWithArgs(Res.string.mcp_port_error_in_use, port)
    McpServerFailure.UNAVAILABLE -> UiText.ResWithArgs(Res.string.mcp_port_error_unavailable, port)
}
