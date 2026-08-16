package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.usecase.CreateCategoryUseCase
import com.neoutils.finsight.domain.usecase.DeleteCategoryUseCase
import com.neoutils.finsight.domain.usecase.UpdateCategoryUseCase
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentCategory
import com.neoutils.finsight.mcp.surface.AgentCategoryWriteAnswer
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.surface.AgentRemovalAnswer
import com.neoutils.finsight.util.AppIcon
import kotlinx.serialization.json.JsonObject

/** The kinds a category can be, spelled as the agent spells them. */
private val CATEGORY_TYPES: Map<String, Category.Type> =
    Category.Type.entries.associateBy { it.name.lowercase() }

/** A category as an agent receives it back from a write — no total, because none was read. */
private fun Category.asAgentCategory() = AgentCategory(
    id = id,
    name = name,
    type = type.name.lowercase(),
    isArchived = isArchived,
)

// ----------------------------------------------------------------------------------
// create_category
// ----------------------------------------------------------------------------------

/** **Creates one of the user's categories, with the ledger dimension its postings are classified by.** */
internal class CreateCategoryTool(
    private val createCategory: CreateCategoryUseCase,
) : McpTool {

    override val name: String = McpToolName.CREATE_CATEGORY.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Create a category to classify postings under. " +
            "The kind is stated once, at creation, and no later edit changes it: it is the axis " +
            "everything already classified under the category was read against, so flipping it " +
            "would restate what past movement meant. " +
            "PERIMETER: a category is an analytic axis, not an account — it holds no money and " +
            "has no currency of its own. It is born with the app's default icon; icons are not " +
            "part of this surface."

    override val inputSchema = schema(
        "name" to text("What the user calls it. Must not clash with a category that already exists, archived ones included."),
        "type" to choice("Whether it classifies spending or earning.", CATEGORY_TYPES.keys.toList()),
        required = listOf("name", "type"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val name = arguments.requiredString("name")
        val type = CATEGORY_TYPES.getValue(
            arguments.requiredOneOf("type", CATEGORY_TYPES.keys.toList()),
        )

        createCategory(
            name = name,
            iconKey = AppIcon.CATEGORY.key,
            type = type,
        ).reported(
            summary = "category $name (${type.name.lowercase()})",
            payload = {
                AgentCategoryWriteAnswer(
                    category = it.asAgentCategory(),
                    note = "Created. Nothing is classified under it yet.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.CATEGORY, it.id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// update_category
// ----------------------------------------------------------------------------------

/** **Edits a category — its name, which is everything about it that is not identity or history.** */
internal class UpdateCategoryTool(
    private val categoryRepository: ICategoryRepository,
    private val updateCategory: UpdateCategoryUseCase,
) : McpTool {

    override val name: String = McpToolName.UPDATE_CATEGORY.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Rename a category. " +
            "PERIMETER: whether a category classifies spending or earning is the user's " +
            "declaration at creation and cannot be changed here — everything already classified " +
            "under it was read against that axis. Archiving is archive_entity, and removing it " +
            "is delete_category."

    override val inputSchema = schema(
        "id" to number("The category to edit, from list_categories."),
        "name" to text("The new name."),
        required = listOf("id", "name"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")
        val name = arguments.requiredString("name")
        val stored = categoryRepository.require(id)

        updateCategory(
            categoryId = id,
            name = name,
            // Kept as it is: the surface does not carry icons, and passing a default would
            // silently reset one the user chose on the screen.
            iconKey = stored.icon.key,
        ).reported(
            summary = "category ${stored.name}",
            payload = {
                AgentCategoryWriteAnswer(
                    category = stored.copy(name = name.trim()).asAgentCategory(),
                    note = "Renamed. What is classified under it is untouched.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.CATEGORY, id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// delete_category
// ----------------------------------------------------------------------------------

/**
 * **Removes a category that was never used, facade and ledger dimension together.**
 *
 * A category with any dependent is refused by `ResolveCategoryRetirabilityUseCase` — the single
 * owner of that rule, and the same one the screens consult — and the refusal names archiving,
 * because that is what the domain allows in its place.
 */
internal class DeleteCategoryTool(
    private val categoryRepository: ICategoryRepository,
    private val deleteCategory: DeleteCategoryUseCase,
) : McpTool {

    override val name: String = McpToolName.DELETE_CATEGORY.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Remove a category for good, together with the ledger dimension its postings are " +
            "classified by. " +
            "PERIMETER: only a category nothing depends on can go. One with postings, one a " +
            "budget watches, one a recurring template classifies by, and the yield category " +
            "while an account still declares that it yields are all refused — the refusal says " +
            "which, and names archive_entity, which keeps the category and everything already " +
            "classified under it while taking it out of every selector."

    override val inputSchema = schema(
        "id" to number("The category to remove, from list_categories."),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")

        val stored = categoryRepository.getCategoryById(id)
            ?: return@writing refusedWith(
                AgentRefusal.notFound("category", id),
                summary = "delete category $id",
            )

        deleteCategory(id).reported(
            summary = "category ${stored.name}",
            payload = {
                AgentRemovalAnswer(
                    removed = "category",
                    id = id,
                    name = stored.name,
                    alsoRemoved = listOf("the ledger dimension its postings were classified by"),
                    note = "Removed. Nothing was classified under it.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.CATEGORY, id) },
        )
    }
}
