package com.neoutils.finsight.mcp.resource

import com.neoutils.finsight.mcp.contract.McpTool
import com.neoutils.finsight.mcp.contract.TOOL_NAME_PREFIX
import kotlinx.serialization.json.JsonObject

/** The scheme every resource this server announces is addressed under. */
const val RESOURCE_SCHEME: String = "finsight"

/**
 * One addressable document this server publishes.
 *
 * A resource is **attached** to a conversation rather than called into it, which is the
 * whole reason these exist beside the tools that answer the same questions: as a tool they
 * depend on the model choosing to call them, and a model that does not call them guesses
 * identifiers.
 */
class McpResource(
    /** `finsight://…` — stable, because a client stores it. */
    val uri: String,
    /** Prefixed and carrying the verb, like every other name this server announces. */
    val name: String,
    val title: String,
    val description: String,
    /** Always `application/json`: the content is the same structured answer the tool gives. */
    val mimeType: String = "application/json",
    private val document: suspend () -> String,
) {

    /** The document, as text. */
    suspend fun read(): String = document()
}

/** Everything this server publishes as a resource. */
class ResourceRegistry(resources: List<McpResource>) {

    val resources: List<McpResource> = resources.sortedBy { it.uri }

    private val byUri = resources.associateBy { it.uri }

    init {
        require(byUri.size == resources.size) {
            "Two resources are published under the same URI: " +
                resources.map { it.uri }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        }
        resources.forEach { resource ->
            require(resource.uri.startsWith("$RESOURCE_SCHEME://")) {
                "`${resource.uri}` is not under the `$RESOURCE_SCHEME` scheme"
            }
            require(resource.name.startsWith(TOOL_NAME_PREFIX)) {
                "`${resource.name}` is not prefixed by `$TOOL_NAME_PREFIX`"
            }
        }
    }

    /** The resource published at [uri], or `null` — which the server answers as not found. */
    fun find(uri: String): McpResource? = byUri[uri]
}

/**
 * The three documents whose job is to be available **before** any decision: what the user
 * holds, the accounts, and the categories.
 *
 * Each one **delegates to the tool that answers the same question**, with no arguments, and
 * publishes exactly what that tool returned. That is deliberate: two producers of "the
 * user's accounts" would be two answers, and the one the model did not read would be the
 * one it acted on.
 *
 * @param overview the overview tool.
 * @param accounts the account listing.
 * @param categories the category listing.
 */
fun orientationResources(
    overview: McpTool,
    accounts: McpTool,
    categories: McpTool,
): ResourceRegistry = ResourceRegistry(
    listOf(
        McpResource(
            uri = "$RESOURCE_SCHEME://overview",
            name = "${TOOL_NAME_PREFIX}read_overview",
            title = "Overview",
            description = "Base currency, net worth per currency, every account with its balance, " +
                "every card with what it owes, and how far the exchange-rate archive reaches. " +
                "The same answer `${overview.name}` gives, available without calling it.",
        ) { overview.document() },
        McpResource(
            uri = "$RESOURCE_SCHEME://accounts",
            name = "${TOOL_NAME_PREFIX}read_accounts",
            title = "Accounts",
            description = "The user's accounts with their identifiers, currencies and balances — " +
                "the identifiers every write takes. The same answer `${accounts.name}` gives.",
        ) { accounts.document() },
        McpResource(
            uri = "$RESOURCE_SCHEME://categories",
            name = "${TOOL_NAME_PREFIX}read_categories",
            title = "Categories",
            description = "The categories that exist, with their identifiers. Classification happens " +
                "in these and no tool creates another. The same answer `${categories.name}` gives.",
        ) { categories.document() },
    ),
)

/** The tool's own answer, with no arguments, as the text of a document. */
private suspend fun McpTool.document(): String = execute(JsonObject(emptyMap())).structuredContent.toString()
