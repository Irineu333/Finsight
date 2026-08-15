package com.neoutils.finsight.mcp.contract

import com.neoutils.finsight.feature.mcp.api.McpPermission
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** The prefix every name this server announces carries. */
const val TOOL_NAME_PREFIX: String = "finsight_"

/**
 * The annotations the protocol defines to describe what a tool does — the **only** channel
 * through which this server communicates risk, and what a client reads to decide when to
 * ask the user for confirmation.
 *
 * They are declarations of fact and they SHALL be true. A tool annotated read-only writes
 * under no argument, which is the whole reason the dry run is a tool of its own rather than
 * a boolean on the write: a tool that is read-only or destructive depending on an argument
 * cannot be annotated honestly.
 *
 * **Annotating is not applying.** The protocol tells clients to treat annotations as
 * untrusted, so every restriction announced here is also imposed at execution, on the
 * server — see [ToolRegistry.isPermitted].
 */
@Serializable
data class ToolAnnotations(
    /** The tool only reads. It persists nothing, whatever it is called with. */
    val readOnlyHint: Boolean,
    /** The tool may remove or overwrite what already exists. */
    val destructiveHint: Boolean = false,
    /** Calling it again with the same arguments has no further effect. */
    val idempotentHint: Boolean = false,
    /** It reaches an open set of entities rather than a closed, known one. */
    val openWorldHint: Boolean = false,
) {
    init {
        require(!(readOnlyHint && destructiveHint)) {
            "A read-only tool cannot be destructive — one of the two annotations is a lie"
        }
    }
}

/**
 * One announced tool.
 *
 * **[outputSchema] is not optional.** The outcome — refusals and warnings included — is
 * returned as structured content under it, and outside structured content a warning is
 * prose that the first host to render only what it has a schema for will drop. Build it
 * with [toolOutcomeSchema] so the envelope is the same across the surface.
 */
interface McpTool {

    /**
     * The announced name: [TOOL_NAME_PREFIX], then the verb, then the subject —
     * `finsight_list_transactions`. Clients aggregate several servers into one namespace,
     * where a generic `list_transactions` collides.
     */
    val name: String

    /** A short human title, for a client that shows one. */
    val title: String

    /**
     * What the tool does, and **what its answer means where the shape could mislead** —
     * that a per-currency collection is a collection even with one element, and that
     * totals come from the aggregation tool, which a listing's description names.
     */
    val description: String

    /** JSON Schema of the arguments. */
    val inputSchema: JsonObject

    /** JSON Schema of the structured content. Mandatory — see the interface note. */
    val outputSchema: JsonObject

    /** What this tool does to the user's data, truthfully. */
    val annotations: ToolAnnotations

    /** Runs the tool. A refusal is a [ToolOutcome.Failed], never an exception. */
    suspend fun execute(arguments: JsonObject): ToolOutcome
}

/**
 * Whether this tool writes.
 *
 * Derived from the annotation and from nothing else, so that what the tool announces and
 * what the server enforces cannot drift apart: making a tool a write is done by telling
 * the truth in its annotations.
 */
val McpTool.isWrite: Boolean get() = !annotations.readOnlyHint

/**
 * Everything this server announces, and the one place the surface's naming and schema
 * rules are enforced.
 *
 * The rules are checked when the registry is built, not when a tool is called: a tool
 * without an output schema or with a name that could collide is a defect of the build, and
 * discovering it on the first call means discovering it in front of a user.
 */
class ToolRegistry(tools: List<McpTool>) {

    val tools: List<McpTool> = tools.sortedBy { it.name }

    private val byName = tools.associateBy { it.name }

    init {
        require(byName.size == tools.size) {
            "Two tools announce the same name: " +
                tools.map { it.name }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        }
        tools.forEach { tool ->
            require(tool.name.startsWith(TOOL_NAME_PREFIX)) {
                "`${tool.name}` is not prefixed by `$TOOL_NAME_PREFIX`"
            }
            require(NAME.matches(tool.name)) {
                "`${tool.name}` is outside the character set the protocol recommends for tool names"
            }
            require(tool.outputSchema.isNotEmpty()) {
                "`${tool.name}` declares no output schema, so its refusals and warnings would be prose"
            }
            require(tool.description.isNotBlank()) { "`${tool.name}` has no description" }
        }
    }

    /** The tool announced under [name], or `null` — which the server answers as not found. */
    fun find(name: String): McpTool? = byName[name]

    /**
     * What a client at this permission level is **told about**.
     *
     * Hiding is for the well-behaved client; [isPermitted] is what holds. Both read the
     * same predicate, which is why a tool cannot be hidden and still executable.
     */
    fun visibleTo(permission: McpPermission): List<McpTool> = tools.filter { isPermitted(it, permission) }

    /**
     * Whether this level may **execute** [tool] — the check the server makes on every
     * call, including for a tool it never announced.
     *
     * A client that ignores the annotations and calls a write at read-only level is
     * refused all the same. That is the difference between announcing a restriction and
     * having one.
     */
    fun isPermitted(tool: McpTool, permission: McpPermission): Boolean = when (permission) {
        McpPermission.READ_ONLY -> !tool.isWrite
        McpPermission.READ_WRITE -> true
    }

    private companion object {
        /**
         * The character set the protocol recommends for a tool name: letters, digits and
         * `_`/`-`. Kept narrow deliberately — a name is a key clients index by.
         */
        val NAME = Regex("""[a-zA-Z0-9_-]{1,64}""")
    }
}
