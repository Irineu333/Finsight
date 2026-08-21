package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.McpPermissionAxis

/**
 * **Every tool this server will ever offer, and nothing else.**
 *
 * The surface is closed by decision, and this enum is where the decision is written. A tool whose
 * name is not a constant here cannot be announced — `McpSurfaceIsClosedTest` compares what the
 * server registers against [McpSurface.offered], which is a set of these — so a tool cannot appear
 * by being written, only by being decided on first.
 *
 * The list is the four families of design D4 — what a screen of the app already does: figures at the
 * top (questions), a list below them (catalogue), a form (registration) and buttons (operations) —
 * and the four families **are** the four permission axes of `mcp-permissions`, which is why [axis]
 * is a property of the name rather than a table somewhere else.
 */
internal enum class McpToolName(
    val axis: McpPermissionAxis,
    val family: McpToolFamily,
) {

    // --- Family 1 — Questions: the app calculates, the agent receives the number ------------

    GET_BALANCE(McpPermissionAxis.READ, McpToolFamily.QUESTIONS),
    GET_MONTH_SUMMARY(McpPermissionAxis.READ, McpToolFamily.QUESTIONS),
    GET_CATEGORY_SPENDING(McpPermissionAxis.READ, McpToolFamily.QUESTIONS),
    GET_CATEGORY_INCOME(McpPermissionAxis.READ, McpToolFamily.QUESTIONS),
    GET_SPENDING_BREAKDOWN(McpPermissionAxis.READ, McpToolFamily.QUESTIONS),
    GET_BUDGET_PROGRESS(McpPermissionAxis.READ, McpToolFamily.QUESTIONS),
    GET_PENDING_RECURRING(McpPermissionAxis.READ, McpToolFamily.QUESTIONS),
    GET_CARD_OVERVIEW(McpPermissionAxis.READ, McpToolFamily.QUESTIONS),
    GET_REPORT_STATS(McpPermissionAxis.READ, McpToolFamily.QUESTIONS),
    GET_NET_WORTH(McpPermissionAxis.READ, McpToolFamily.QUESTIONS),

    // --- Family 2 — Catalogue: how the agent discovers identities and names ------------------

    LIST_TRANSACTIONS(McpPermissionAxis.READ, McpToolFamily.CATALOGUE),
    GET_TRANSACTION(McpPermissionAxis.READ, McpToolFamily.CATALOGUE),
    LIST_ACCOUNTS(McpPermissionAxis.READ, McpToolFamily.CATALOGUE),
    LIST_CATEGORIES(McpPermissionAxis.READ, McpToolFamily.CATALOGUE),
    LIST_CARDS(McpPermissionAxis.READ, McpToolFamily.CATALOGUE),
    LIST_INVOICES(McpPermissionAxis.READ, McpToolFamily.CATALOGUE),
    GET_INVOICE(McpPermissionAxis.READ, McpToolFamily.CATALOGUE),
    LIST_INSTALLMENTS(McpPermissionAxis.READ, McpToolFamily.CATALOGUE),
    LIST_BUDGETS(McpPermissionAxis.READ, McpToolFamily.CATALOGUE),
    LIST_RECURRING(McpPermissionAxis.READ, McpToolFamily.CATALOGUE),

    // --- Family 3 — Registration: creating and altering, and — on its own axis — removing ----

    CREATE_TRANSACTION(McpPermissionAxis.RECORD, McpToolFamily.REGISTRATION),
    UPDATE_TRANSACTION(McpPermissionAxis.RECORD, McpToolFamily.REGISTRATION),
    DELETE_TRANSACTION(McpPermissionAxis.REMOVE, McpToolFamily.REGISTRATION),
    CREATE_ACCOUNT(McpPermissionAxis.RECORD, McpToolFamily.REGISTRATION),
    UPDATE_ACCOUNT(McpPermissionAxis.RECORD, McpToolFamily.REGISTRATION),
    DELETE_ACCOUNT(McpPermissionAxis.REMOVE, McpToolFamily.REGISTRATION),
    CREATE_CATEGORY(McpPermissionAxis.RECORD, McpToolFamily.REGISTRATION),
    UPDATE_CATEGORY(McpPermissionAxis.RECORD, McpToolFamily.REGISTRATION),
    DELETE_CATEGORY(McpPermissionAxis.REMOVE, McpToolFamily.REGISTRATION),
    CREATE_CARD(McpPermissionAxis.RECORD, McpToolFamily.REGISTRATION),
    UPDATE_CARD(McpPermissionAxis.RECORD, McpToolFamily.REGISTRATION),
    DELETE_CARD(McpPermissionAxis.REMOVE, McpToolFamily.REGISTRATION),
    CREATE_BUDGET(McpPermissionAxis.RECORD, McpToolFamily.REGISTRATION),
    UPDATE_BUDGET(McpPermissionAxis.RECORD, McpToolFamily.REGISTRATION),
    DELETE_BUDGET(McpPermissionAxis.REMOVE, McpToolFamily.REGISTRATION),
    CREATE_RECURRING(McpPermissionAxis.RECORD, McpToolFamily.REGISTRATION),
    UPDATE_RECURRING(McpPermissionAxis.RECORD, McpToolFamily.REGISTRATION),
    DELETE_RECURRING(McpPermissionAxis.REMOVE, McpToolFamily.REGISTRATION),
    CREATE_INSTALLMENT(McpPermissionAxis.RECORD, McpToolFamily.REGISTRATION),
    UPDATE_INSTALLMENT(McpPermissionAxis.RECORD, McpToolFamily.REGISTRATION),
    DELETE_INSTALLMENT(McpPermissionAxis.REMOVE, McpToolFamily.REGISTRATION),
    CREATE_INVOICE(McpPermissionAxis.RECORD, McpToolFamily.REGISTRATION),
    DELETE_INVOICE(McpPermissionAxis.REMOVE, McpToolFamily.REGISTRATION),

    // --- Family 4 — Operations: what moves money or moves a life cycle -----------------------

    PAY_INVOICE(McpPermissionAxis.OPERATE, McpToolFamily.OPERATIONS),
    ADVANCE_INVOICE_PAYMENT(McpPermissionAxis.OPERATE, McpToolFamily.OPERATIONS),
    CLOSE_INVOICE(McpPermissionAxis.OPERATE, McpToolFamily.OPERATIONS),
    OPEN_INVOICE(McpPermissionAxis.OPERATE, McpToolFamily.OPERATIONS),
    REOPEN_INVOICE(McpPermissionAxis.OPERATE, McpToolFamily.OPERATIONS),
    ADJUST_INVOICE(McpPermissionAxis.OPERATE, McpToolFamily.OPERATIONS),
    ADJUST_BALANCE(McpPermissionAxis.OPERATE, McpToolFamily.OPERATIONS),
    TRANSFER(McpPermissionAxis.OPERATE, McpToolFamily.OPERATIONS),
    SET_DEFAULT_ACCOUNT(McpPermissionAxis.OPERATE, McpToolFamily.OPERATIONS),
    CONFIRM_RECURRING(McpPermissionAxis.OPERATE, McpToolFamily.OPERATIONS),
    SKIP_RECURRING(McpPermissionAxis.OPERATE, McpToolFamily.OPERATIONS),
    ARCHIVE_ENTITY(McpPermissionAxis.OPERATE, McpToolFamily.OPERATIONS),
    UNARCHIVE_ENTITY(McpPermissionAxis.OPERATE, McpToolFamily.OPERATIONS),
    ;

    /** The identity the agent calls it by, and the identity the activity log keeps. */
    val wireName: String = name.lowercase()

    companion object {

        private val byWireName: Map<String, McpToolName> = entries.associateBy { it.wireName }

        /**
         * The tool a wire name denotes.
         *
         * It throws rather than answering `null` because the surface is closed: a name that reached
         * the server without being one of these is a tool that was written and never decided on, and
         * the axis governing it would be a guess. `McpSurfaceIsClosedTest` keeps that from ever
         * being a production question, so the throw is a defect report and not a runtime path.
         */
        fun of(wireName: String): McpToolName = byWireName[wireName]
            ?: error("`$wireName` is not a tool this surface decided on.")
    }
}

/**
 * The shape of the app a family answers for — a screen's four parts, with an agent in the place of
 * the person reading it.
 *
 * Distinct from [McpPermissionAxis] only where removal is: a family is *what a tool is for*, and an
 * axis is *what a user grants*. The registration family holds both, which is exactly why the two
 * are separate properties.
 */
internal enum class McpToolFamily {

    /** Figures at the top of a screen: the app calculates, the agent receives the number. */
    QUESTIONS,

    /** The list below them: how identities and names are discovered. */
    CATALOGUE,

    /** The form: what is created, altered and removed. */
    REGISTRATION,

    /** The buttons: what moves money or moves something through its life cycle. */
    OPERATIONS,
}
