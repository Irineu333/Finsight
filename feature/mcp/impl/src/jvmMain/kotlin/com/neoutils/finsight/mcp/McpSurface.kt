package com.neoutils.finsight.mcp

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
     * Empty while the surface is being built. Each family adds its own names here in the same
     * change that registers the tools, which is what keeps the two from drifting: an addition on
     * either side alone fails the test.
     */
    val offered: Set<McpToolName> = emptySet()

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
