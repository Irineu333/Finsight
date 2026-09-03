package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.McpPermissionAxis

/**
 * **What the surface offers, and what it deliberately does not.**
 *
 * [offered] is the declaration `McpSurfaceIsClosedTest` holds the registry against, in both
 * directions: a tool registered without being declared here is one that entered without a decision,
 * and a tool declared here without being registered is one that disappeared without anyone
 * noticing. Building a tool therefore takes two edits — writing it, and naming it here — and the
 * second one is the decision.
 *
 * [exclusions] is the other half, and it is the half nothing else could supply. A capability the
 * surface does not reach leaves no trace: a tool that was forgotten and a tool that was refused look
 * exactly alike from the list of what exists, and reading the list is how everyone checks. So the
 * absences are written down, each with the reason it is one.
 */
internal object McpSurface {

    /**
     * The tools the server announces today.
     *
     * The surface was built one family at a time, and each family added its own names here in the
     * same change that registered the tools — which is what keeps the two from drifting: an
     * addition on either side alone fails the test.
     *
     * All four are here. Family 1, the questions: the app calculates and the agent receives the
     * number. Family 2, the catalogue: what exists, what it is called, and the figure that belongs
     * beside it — the family an agent resolves a name into an identity through. Family 3, the
     * registration — what is created, altered and removed —, which straddles two axes: fifteen
     * tools a user grants by saying "record and edit", and eight `delete_*` on an axis of its own.
     * And family 4, the operations: thirteen tools that move money or move a life cycle.
     */
    val offered: Set<McpToolName> = setOf(
        McpToolName.GET_BALANCE,
        McpToolName.GET_NET_WORTH,
        McpToolName.GET_MONTH_SUMMARY,
        McpToolName.GET_CATEGORY_SPENDING,
        McpToolName.GET_CATEGORY_INCOME,
        McpToolName.GET_SPENDING_BREAKDOWN,
        McpToolName.GET_BUDGET_PROGRESS,
        McpToolName.GET_PENDING_RECURRING,
        McpToolName.GET_CARD_OVERVIEW,
        McpToolName.GET_REPORT_STATS,
        McpToolName.LIST_TRANSACTIONS,
        McpToolName.GET_TRANSACTION,
        McpToolName.LIST_ACCOUNTS,
        McpToolName.LIST_CARDS,
        McpToolName.LIST_CATEGORIES,
        McpToolName.LIST_INVOICES,
        McpToolName.GET_INVOICE,
        McpToolName.LIST_INSTALLMENTS,
        McpToolName.LIST_BUDGETS,
        McpToolName.LIST_RECURRING,

        // Family 3 — recording and editing.
        McpToolName.CREATE_TRANSACTION,
        McpToolName.UPDATE_TRANSACTION,
        McpToolName.CREATE_ACCOUNT,
        McpToolName.UPDATE_ACCOUNT,
        McpToolName.CREATE_CARD,
        McpToolName.UPDATE_CARD,
        McpToolName.CREATE_CATEGORY,
        McpToolName.UPDATE_CATEGORY,
        McpToolName.CREATE_BUDGET,
        McpToolName.UPDATE_BUDGET,
        McpToolName.CREATE_RECURRING,
        McpToolName.UPDATE_RECURRING,
        McpToolName.CREATE_INSTALLMENT,
        McpToolName.UPDATE_INSTALLMENT,
        McpToolName.CREATE_INVOICE,

        // Family 3 — removing, which is its own axis: granting "record and edit" without this one
        // leaves an agent that creates and alters and does not remove.
        McpToolName.DELETE_TRANSACTION,
        McpToolName.DELETE_ACCOUNT,
        McpToolName.DELETE_CARD,
        McpToolName.DELETE_CATEGORY,
        McpToolName.DELETE_BUDGET,
        McpToolName.DELETE_RECURRING,
        McpToolName.DELETE_INSTALLMENT,
        McpToolName.DELETE_INVOICE,

        // Family 4 — the operations, on the axis of their own: what moves money or moves
        // something through its life cycle. `pay_invoice` is here and `mark_invoice_paid` is
        // nowhere, which is the decision this list exists to record.
        McpToolName.PAY_INVOICE,
        McpToolName.ADVANCE_INVOICE_PAYMENT,
        McpToolName.CLOSE_INVOICE,
        McpToolName.OPEN_INVOICE,
        McpToolName.REOPEN_INVOICE,
        McpToolName.ADJUST_INVOICE,
        McpToolName.ADJUST_BALANCE,
        McpToolName.TRANSFER,
        McpToolName.SET_DEFAULT_ACCOUNT,
        McpToolName.CONFIRM_RECURRING,
        McpToolName.SKIP_RECURRING,
        McpToolName.ARCHIVE_ENTITY,
        McpToolName.UNARCHIVE_ENTITY,
    )

    /**
     * What each axis governs, which is the same fact [offered] already holds — read the other way
     * round.
     *
     * Derived and never written down twice: the axis is a property of the tool's own name, so the
     * grouping cannot drift from the surface the way a hand-kept table would.
     */
    val offeredByAxis: Map<McpPermissionAxis, Set<McpToolName>> =
        McpPermissionAxis.entries.associateWith { axis -> offered.filterTo(mutableSetOf()) { it.axis == axis } }

    /**
     * How many tools each axis grants — the number the settings section tells the user before they
     * flip a switch, because a switch whose effect is not stated is granted blind.
     *
     * The one place it is counted. Two places counting would be two answers, one edit apart.
     */
    val toolCountByAxis: Map<McpPermissionAxis, Int> = offeredByAxis.mapValues { (_, tools) -> tools.size }

    /** The tools a set of granted axes reaches — what `tools/list` announces, as a set of names. */
    fun offeredUnder(granted: Set<McpPermissionAxis>): Set<McpToolName> =
        offered.filterTo(mutableSetOf()) { it.axis in granted }

    /** What the app can do that the surface does not reach, and why each one is out. */
    val exclusions: List<McpSurfaceExclusion> = listOf(
        McpSurfaceExclusion(
            capability = "Writing an exchange rate, and the currency catalogue the rates hang from",
            kind = McpSurfaceExclusion.Kind.WITHHELD,
            reason = "One rate rewrites every consolidated figure in the app, silently and " +
                "retroactively — closed months included — and produces no posting that would " +
                "show it happened. The agent reads the rate that was applied; it does not write " +
                "one.",
        ),
        McpSurfaceExclusion(
            capability = "Changing the base currency",
            kind = McpSurfaceExclusion.Kind.WITHHELD,
            reason = "The same damage by the other door: every figure the app consolidates is " +
                "re-denominated at once, with nothing in the ledger recording that anything " +
                "changed.",
        ),
        McpSurfaceExclusion(
            capability = "Administering the server itself — its port, its token, its permissions",
            kind = McpSurfaceExclusion.Kind.WITHHELD,
            reason = "An agent that can widen its own permissions has none. What the user granted " +
                "is changed on the app's own screen, by the user.",
        ),
        McpSurfaceExclusion(
            capability = "Support",
            kind = McpSurfaceExclusion.Kind.OUT_OF_SCOPE,
            reason = "The only surface of the app that leaves the machine.",
        ),
        McpSurfaceExclusion(
            capability = "Configuring, rendering and exporting a report",
            kind = McpSurfaceExclusion.Kind.OUT_OF_SCOPE,
            reason = "`get_report_stats` answers with the figures; assembling a document is a " +
                "visual artefact rather than data.",
        ),
        McpSurfaceExclusion(
            capability = "Dashboard preferences, including which accounts sit outside the total",
            kind = McpSurfaceExclusion.Kind.OUT_OF_SCOPE,
            reason = "Changing them changes the number the agent itself reads afterwards.",
        ),
        McpSurfaceExclusion(
            capability = "Posting a yield",
            kind = McpSurfaceExclusion.Kind.OUT_OF_SCOPE,
            reason = "The most arguable line drawn here — it is a posting like any other. Out " +
                "because it was not in scope and because an account that yields carries a rule " +
                "of its own.",
        ),
        McpSurfaceExclusion(
            capability = "Icons for accounts, cards and categories",
            kind = McpSurfaceExclusion.Kind.OUT_OF_SCOPE,
            reason = "What an agent creates is born with the default. A visual catalogue does not " +
                "translate into JSON.",
        ),
        McpSurfaceExclusion(
            capability = "Seeding the default categories",
            kind = McpSurfaceExclusion.Kind.OUT_OF_SCOPE,
            reason = "First-run seeding, not a thing a user does.",
        ),
        McpSurfaceExclusion(
            capability = "Authentication",
            kind = McpSurfaceExclusion.Kind.OUT_OF_SCOPE,
            reason = "The server inherits the app's session; it does not manage it.",
        ),
        McpSurfaceExclusion(
            capability = "Telemetry and window state",
            kind = McpSurfaceExclusion.Kind.OUT_OF_SCOPE,
            reason = "Not the user's data.",
        ),
        McpSurfaceExclusion(
            capability = "Driving the UI and reading screen state",
            kind = McpSurfaceExclusion.Kind.OUT_OF_SCOPE,
            reason = "Exposing `UiState` would freeze the UI into a contract.",
        ),
        McpSurfaceExclusion(
            capability = "Android and iOS",
            kind = McpSurfaceExclusion.Kind.OUT_OF_SCOPE,
            reason = "A local server needs a process that owns a socket, which is the desktop.",
        ),
        McpSurfaceExclusion(
            capability = "Idempotency of a write",
            kind = McpSurfaceExclusion.Kind.OUT_OF_SCOPE,
            reason = "Repeating a lost call duplicates the posting. Acknowledged, not solved — " +
                "which is why the activity log puts the two side by side rather than hiding one.",
        ),
        McpSurfaceExclusion(
            capability = "Capturing and configuring backups — manual export, the automatic " +
                "vault, retention",
            kind = McpSurfaceExclusion.Kind.OUT_OF_SCOPE,
            reason = "Harmless on its own, since it only copies the archive out, and simply " +
                "not reached.",
        ),
        McpSurfaceExclusion(
            capability = "Restoring the database from a backup",
            kind = McpSurfaceExclusion.Kind.OUT_OF_SCOPE,
            reason = "Not reached, though it carries the same shape of damage as writing a " +
                "rate or moving the base currency: one call replaces everything in the ledger " +
                "at once, irreversibly outside the app. Acknowledged rather than forbidden by " +
                "a written requirement the way those two are — a decision for whoever owns " +
                "the surface.",
        ),
    )

    /** The exclusions a requirement forbids offering, as opposed to those merely not reached. */
    val withheld: List<McpSurfaceExclusion>
        get() = exclusions.filter { it.kind == McpSurfaceExclusion.Kind.WITHHELD }
}

/**
 * One capability of the app that the surface does not reach, and the reason it does not.
 *
 * The reason is the point. Without it the entry is indistinguishable from an oversight, which is
 * the very thing the list exists to rule out.
 */
internal data class McpSurfaceExclusion(
    val capability: String,
    val kind: Kind,
    val reason: String,
) {
    /** Why a capability is absent — which is a different fact from that it is absent. */
    enum class Kind {

        /**
         * A requirement forbids offering it. The damage is asymmetric and silent: it lands on
         * everything at once and produces nothing that reports it.
         */
        WITHHELD,

        /** Simply not reached. Nothing forbids it, and nothing about it is dangerous. */
        OUT_OF_SCOPE,
    }
}
