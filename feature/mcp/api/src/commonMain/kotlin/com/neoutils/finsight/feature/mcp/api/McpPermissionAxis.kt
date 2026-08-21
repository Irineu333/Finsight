package com.neoutils.finsight.feature.mcp.api

/**
 * The four things a user grants an agent, independently of each other.
 *
 * They are independent because they are four different questions and not degrees of one: removing is
 * not an intense edit, and paying an invoice moves money between accounts, which recording does not.
 * Granting one grants nothing else.
 *
 * **A granted axis decides which tools *exist*.** It is not an `if` at the top of a tool body: a
 * capability the user did not grant is not announced in `tools/list` at all, so the agent does not
 * try it, does not fail, and does not spend context on it. The refusal on execution stays, because
 * the announcement is a consequence of the permission and not its only application.
 *
 * It lives in the `api` because the user is the one who moves these: the settings section reads and
 * writes them through [McpServerController], and the server itself never offers them to an agent — a
 * client able to widen its own permissions has none.
 */
enum class McpPermissionAxis {

    /** Consult figures and list what exists. The only axis a freshly enabled server grants. */
    READ,

    /** Create and alter — postings, accounts, cards, categories, budgets, plans, templates. */
    RECORD,

    /**
     * Remove permanently.
     *
     * An axis of its own, and every removal of the surface is on it. "Remove definitively" names no
     * entity, and its scenario is literal: granting "record and edit" without this one leaves an
     * agent that *creates and alters, and does not remove*. A removal parked on the recording axis
     * would remove under that grant, and the user would never have been asked.
     */
    REMOVE,

    /** Move money, pay, close, reopen and adjust invoices, confirm and skip cycles, archive. */
    OPERATE,
    ;

    /** The identity the axis is persisted and spoken of by, stable across renamings of the label. */
    val key: String = name.lowercase()

    companion object {

        /**
         * What a server switched on for the first time grants: reading, and nothing else.
         *
         * Writing waits for a second, explicit act by the user. An agent that could record, remove
         * or operate the moment the switch was flipped would be doing so under a permission nobody
         * was asked for.
         */
        val INITIAL: Set<McpPermissionAxis> = setOf(READ)
    }
}
