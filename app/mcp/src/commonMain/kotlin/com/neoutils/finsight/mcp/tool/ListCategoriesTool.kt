@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.mcp.contract.ArchivedScope
import com.neoutils.finsight.mcp.contract.AssumedDefaults
import com.neoutils.finsight.mcp.contract.Cursor
import com.neoutils.finsight.mcp.contract.McpTool
import com.neoutils.finsight.mcp.contract.PageLimit
import com.neoutils.finsight.mcp.contract.ResponseLimits
import com.neoutils.finsight.mcp.contract.TOOL_NAME_PREFIX
import com.neoutils.finsight.mcp.contract.ToolAnnotations
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.resolvePageLimit
import com.neoutils.finsight.mcp.contract.toolOutcomeSchema
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The name of the tool that lists categories, named by the write path and the resources. */
const val LIST_CATEGORIES_TOOL: String = "${TOOL_NAME_PREFIX}list_categories"

/**
 * The categories a transaction can be classified in — and the only ones it can.
 *
 * **No tool of this delivery creates a category.** An agent importing a statement that
 * could create one would produce a variation of the same category with every statement,
 * and that is expensive to undo; so classification happens in what already exists, and a
 * write naming a category that does not exist is refused by identifier, naming what was
 * asked for.
 *
 * "Uncategorized" is **not** in this listing and never will be: in the ledger it is the
 * *absence* of a dimension on the nominal leg, not a category and not a bucket account.
 * The listing and aggregation tools express it as a state of their category filter.
 */
class ListCategoriesTool(
    private val categories: ICategoryRepository,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : McpTool {

    override val name: String = LIST_CATEGORIES_TOOL

    override val title: String = "List categories"

    override val description: String = """
        The categories transactions can be classified in, with the identifiers the write
        tools take. No tool creates a category: classification happens in what exists.

        `type` is the user's own declaration — an expense category or an income one — and
        it is primary state rather than something derived from the ledger.

        Being unclassified is not a category and is absent from this listing; it is a
        state of the category filter of $LIST_TRANSACTIONS_TOOL and
        $AGGREGATE_TRANSACTIONS_TOOL.

        Archived categories are left out unless asked for, and the scope applied comes
        back in `assumed`.
    """.trimIndent()

    override val inputSchema: JsonObject = objectSchema {
        pagingProperties()
        archivedProperty()
        enumProperty("type", listOf("INCOME", "EXPENSE"), "Only categories of this declared type.")
    }

    override val outputSchema: JsonObject = toolOutcomeSchema(
        resultSchema = listingSchema(
            itemsName = "categories",
            item = categorySchema,
            description = "The categories that exist. Being unclassified is not one of them.",
        ),
        errorCodes = CommonToolCodes.all +
            ResponseLimits.CODE_PAGE_LIMIT_ABOVE_CEILING +
            ResponseLimits.CODE_PAGE_LIMIT_NOT_POSITIVE,
    )

    override val annotations: ToolAnnotations = ToolAnnotations(readOnlyHint = true)

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        val args = Arguments(arguments)
        val archived = args.enum("archived", ArchivedScope.entries.toTypedArray())
        val type = args.enum("type", Category.Type.entries.toTypedArray())
        val requestedLimit = args.int("limit")
        val cursor = args.string("cursor")?.let(::Cursor)
        args.failure?.let { return ToolOutcome.Failed(it) }

        val limit = when (val resolved = resolvePageLimit(requestedLimit)) {
            is PageLimit.Refused -> return ToolOutcome.Failed(resolved.error)
            is PageLimit.Accepted -> resolved.limit
        }

        val assumed = AssumedDefaults.resolve(
            today = clock.today(timeZone),
            timeZone = timeZone,
            archived = archived,
        )

        val visible = categories.inScope(assumed.archived.value)
            .filter { type == null || it.type == type }

        val page = paginate(visible, limit, cursor) { it.id.toString() }

        return ok {
            putPage("categories", page.with(page.items.map { buildJsonObject { putCategory(it) } }))
            putAssumed(assumed)
        }
    }
}

/** The categories a scope admits, read from the facade that owns the distinction. */
internal suspend fun ICategoryRepository.inScope(scope: ArchivedScope): List<Category> = when (scope) {
    ArchivedScope.EXCLUDED -> getAllCategories()
    ArchivedScope.INCLUDED -> getAllCategoriesIncludingClosed()
    ArchivedScope.ONLY -> getAllCategoriesIncludingClosed().filter { it.isArchived }
}

internal fun JsonObjectBuilder.putCategory(category: Category) {
    put("id", category.id)
    put("name", category.name)
    put("type", category.type.name)
    put("isArchived", category.isArchived)
    category.systemKey?.let { put("systemKey", it) }
}

internal val categorySchema: JsonObject = objectSchema(required = listOf("id", "name", "type")) {
    integerProperty("id", "The opaque identifier. A name is never a key on this surface.")
    stringProperty("name", "What the user calls this category.")
    enumProperty("type", listOf("INCOME", "EXPENSE"), "The user's declaration, not a derivation.")
    booleanProperty("isArchived", "Retired: it keeps its history and is no longer offered.")
    stringProperty("systemKey", "Present on a category the app provides; identification is by key, never by name.")
}
