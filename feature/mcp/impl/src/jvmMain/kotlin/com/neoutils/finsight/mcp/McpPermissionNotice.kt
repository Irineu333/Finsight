package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.feature.mcp.api.McpPermissionAxis
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.tool.agentJson

/**
 * **What is withheld is said, even though it is not offered.**
 *
 * Filtering `tools/list` does what it promises — the agent does not attempt what it may not do — and
 * produces one effect the filter alone cannot fix: to a client that knows the app only by the list
 * of tools, *withheld* and *non-existent* are the same thing. The simulation this exists because of
 * is on record. Asked to delete a posting on a server with the removal axis off, an agent answered
 * *"there is no delete tool on this server"* — a false statement about the app, made confidently to
 * its owner, which blocks the very action that would resolve the case: the user granting the axis.
 * The same agent reported having considered zeroing the amount through the edit tool to "neutralise"
 * the posting, which is what a refusal with no explanation invites (design D13).
 *
 * So two things are said, and both come from here so they cannot come apart:
 *
 * - the **session handshake** names the capabilities granted and the capabilities withheld, and says
 *   the withholding is the user's own choice and where to reverse it;
 * - a tool **invoked by name** without its permission refuses saying the operation *exists and is
 *   not authorised* — never that it is unknown — with the same indication of where to grant it.
 *
 * **What is named is the capability, never the tools.** The declaration is not a second `tools/list`
 * arriving by another channel: it carries no tool name and no argument, and
 * `McpPermissionsOverTheProtocolTest` holds it to that by refusing any wire identifier in the text.
 */
internal object McpPermissionNotice {

    /**
     * Where a withheld capability is granted — the one sentence, used by the handshake and by every
     * refusal alike.
     *
     * Written once because "the same indication of where to grant it" is a requirement rather than a
     * coincidence: two wordings would be two places for the user to be sent, one edit from
     * disagreeing.
     */
    const val WHERE_TO_GRANT: String =
        "this user switched it off, and only they can switch it back on, in the app's own settings " +
            "under the MCP server section"

    /** What the agent may do now, and what it may not, as the session's opening instructions. */
    fun instructions(granted: Set<McpPermissionAxis>): String {
        val withheld = McpPermissionAxis.entries.filterNot { it in granted }

        return buildString {
            append(PREAMBLE)
            append("\n\n")
            append(
                if (granted.isEmpty()) {
                    "Granted right now: nothing. This user has switched every capability off."
                } else {
                    "Granted right now: " + McpPermissionAxis.entries
                        .filter { it in granted }
                        .joinToString(separator = "; ") { "${it.capability} — ${it.grants}" } + "."
                },
            )
            if (withheld.isEmpty()) return@buildString

            append("\n\n")
            append(
                "Withheld by this user: " + withheld
                    .joinToString(separator = "; ") { "${it.capability} — ${it.grants}" } + ".",
            )
            append("\n\n")
            append("Nothing is missing from the app: where a capability is withheld, $WHERE_TO_GRANT. ")
            append(WITHHELD_MEANS)
        }
    }

    /**
     * The answer to a tool called by name whose axis is not granted.
     *
     * It says the operation exists, which is the whole point: an agent told a name is unknown
     * reports back that the app cannot do the thing, and the user never learns that a switch of
     * theirs is what stopped it. Nothing is offered in its place — `try_instead` stays empty —
     * because pointing at a granted tool that approximates the effect is the contortion this refusal
     * exists to close off.
     */
    fun refusal(tool: McpTool): McpToolResult {
        val refusal = AgentRefusal(
            reason = "`${tool.name}` exists on this server and is not authorised. It is on the " +
                "${tool.axis.capability} capability, which is withheld: $WHERE_TO_GRANT. Say the " +
                "app does this and is waiting on their permission — never that it cannot — and do " +
                "not use another tool to imitate the effect.",
        )

        return McpToolResult(
            text = agentJson.encodeToString(refusal),
            outcome = AgentActivity.Outcome.REFUSED,
            // The act is the attempt itself: nothing was resolved, so the operation's own name is
            // all there is to describe it with, and the log still has to hold the attempt.
            summary = tool.name,
            detail = refusal.reason,
        )
    }

    /**
     * What the app is, in the sentence a client hands the model before the first question.
     *
     * It is the reason `instructions` is the right channel for this and a tool would be the wrong
     * one: the model reads this without being asked to, whereas a tool it never calls tells it
     * nothing.
     */
    private const val PREAMBLE: String =
        "This is the user's own finance app, running on their machine. Every figure you read here " +
            "is the app's own calculation — take it as given rather than recomputing it from parts."

    /** What to answer when the user asks for something a withheld capability covers. */
    private const val WITHHELD_MEANS: String =
        "When they ask for something it covers, tell them the app does it and that it is waiting " +
            "on their permission, and where to give it. Never tell them the app cannot do it, and " +
            "never reach for a granted capability to imitate one that is withheld."
}

/**
 * What the axis is called when it is spoken about — to the agent in the handshake, and to the user
 * in the settings section.
 *
 * The four names of the requirement, in English: read, record and edit, remove, operate.
 */
internal val McpPermissionAxis.capability: String
    get() = when (this) {
        McpPermissionAxis.READ -> "read"
        McpPermissionAxis.RECORD -> "record and edit"
        McpPermissionAxis.REMOVE -> "remove"
        McpPermissionAxis.OPERATE -> "operate"
    }

/**
 * What granting the axis authorises, in capabilities.
 *
 * Not one tool name, and not one argument: naming them here would rebuild the very list the
 * permission removed, and hand back the context the filtering saved.
 */
internal val McpPermissionAxis.grants: String
    get() = when (this) {
        McpPermissionAxis.READ ->
            "consult figures and list what exists"

        McpPermissionAxis.RECORD ->
            "create and change postings, accounts, cards, categories, budgets, instalment plans " +
                "and recurring templates"

        McpPermissionAxis.REMOVE ->
            "delete any of those permanently"

        McpPermissionAxis.OPERATE ->
            "move money between accounts, pay, close, reopen and adjust invoices, confirm and skip " +
                "recurring cycles, and archive or bring back"
    }
