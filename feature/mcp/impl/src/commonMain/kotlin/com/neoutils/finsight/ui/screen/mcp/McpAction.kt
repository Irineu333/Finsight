package com.neoutils.finsight.ui.screen.mcp

import com.neoutils.finsight.feature.mcp.api.McpPermissionAxis

/** Everything the user can do in the MCP server section. */
sealed interface McpAction {

    /** The one decision the section asks for before anything else is shown. */
    data class SetEnabled(val enabled: Boolean) : McpAction

    /**
     * Grants or withholds one capability. One axis per action, because the four are independent and
     * an action carrying a set would be a shape in which granting one could carry another along.
     */
    data class SetPermission(val axis: McpPermissionAxis, val granted: Boolean) : McpAction

    /**
     * Moves the server to [port].
     *
     * It carries the port already chosen, never the typing on the way to it: the number is settled
     * where it is collected, and what reaches here is a decision rather than a keystroke — a port
     * applied per keystroke would rebind on "8", then "84", then "847" on the way to 8477.
     */
    data class ChangePort(val port: Int) : McpAction

    /** Shows the token, or hides it again. It is masked until this is asked for. */
    data object ToggleTokenVisibility : McpAction

    /** Mints a new token; the previous one stops being accepted. */
    data object RegenerateToken : McpAction

    /** Ends the sessions in progress without taking the server down. */
    data object DisconnectSessions : McpAction

    /** Empties the activity log. No posting is touched. */
    data object ClearActivity : McpAction
}
