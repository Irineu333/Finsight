package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.DeleteRecurringUseCase
import com.neoutils.finsight.domain.usecase.SaveRecurringUseCase
import com.neoutils.finsight.extension.isAccept
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentFigure
import com.neoutils.finsight.mcp.surface.AgentRecurring
import com.neoutils.finsight.mcp.surface.AgentRecurringWriteAnswer
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.surface.AgentRemovalAnswer
import kotlinx.serialization.json.JsonObject

/** What a template can post: the two directions a form offers, and the correction it does not. */
private val RECURRING_TYPES: Map<String, TransactionType> = mapOf(
    "expense" to TransactionType.EXPENSE,
    "income" to TransactionType.INCOME,
)

/**
 * A template as an agent receives it back from a write.
 *
 * The amount is denominated by the account or card the template posts through, which is the only
 * place a currency is stated; a template pointing at neither states none, and the figure says so
 * rather than borrowing the base.
 */
internal fun Recurring.asAgentRecurring() = AgentRecurring(
    id = id,
    type = type.name.lowercase(),
    title = label,
    amount = (account?.currency ?: creditCard?.currency)
        ?.let { AgentFigure.exact(amount, it) }
        ?: AgentFigure(amount = null, currency = null, byCurrency = emptyList(), isApproximate = false),
    dayOfMonth = dayOfMonth,
    category = category?.name,
    categoryId = category?.id,
    account = account?.name,
    accountId = account?.id,
    card = creditCard?.name,
    cardId = creditCard?.id,
    isArchived = isArchived,
)

// ----------------------------------------------------------------------------------
// create_recurring
// ----------------------------------------------------------------------------------

/**
 * **Creates a monthly template.**
 *
 * A template is not a posting: nothing reaches the ledger until a cycle is confirmed, which is
 * `confirm_recurring`'s doing. That is why this writes no money and the answer carries no balance.
 */
internal class CreateRecurringTool(
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val categoryRepository: ICategoryRepository,
    private val saveRecurring: SaveRecurringUseCase,
) : McpTool {

    override val name: String = McpToolName.CREATE_RECURRING.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Create a monthly template — something that repeats on the same day every month, like " +
            "a salary or a subscription. " +
            "Give exactly one of account_id or card_id; an income always goes to an account, so " +
            "a card_id with type income is refused. A category classifies one direction only, " +
            "and a category_id the type cannot carry is refused rather than dropped. " +
            "PERIMETER: a template posts nothing by itself. Each month's cycle waits to be " +
            "confirmed (confirm_recurring) or skipped (skip_recurring), and only confirming " +
            "puts money in the ledger — get_pending_recurring lists what is waiting. To record " +
            "something that already happened use create_transaction."

    override val inputSchema = schema(
        "type" to choice("Whether the cycle takes money out or brings it in.", RECURRING_TYPES.keys.toList()),
        "amount" to amount("How much each cycle is, in the currency of the account or card — 45.90, not 4590."),
        "day_of_month" to number("The day the cycle falls on, 1 to 31. A day past the month's end is clamped to it."),
        "title" to text("What it is. Required unless a category is given."),
        "category_id" to number("The category each cycle is classified under, from list_categories."),
        "account_id" to number("The account the cycle posts through, from list_accounts."),
        "card_id" to number("The card the cycle is charged to, from list_cards. Expenses only."),
        required = listOf("type", "amount", "day_of_month"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val type = RECURRING_TYPES.getValue(
            arguments.requiredOneOf("type", RECURRING_TYPES.keys.toList()),
        )
        val amount = arguments.requiredMoney("amount")
        val dayOfMonth = arguments.requiredLong("day_of_month")
        val title = arguments.string("title")

        val account = arguments.long("account_id")?.let { accountRepository.require(it) }
        val card = arguments.long("card_id")?.let { creditCardRepository.require(it) }
        val category = arguments.long("category_id")?.let { categoryRepository.require(it) }

        val summary = "recurring ${title ?: category?.name ?: "untitled"}, $amount on day $dayOfMonth"

        if (account != null && card != null) {
            return@writing refusedWith(
                AgentRefusal(
                    reason = "A cycle posts in one place: give `account_id` or `card_id`, not both.",
                ),
                summary = summary,
            )
        }

        // `RecurringForm.toRecurring` settles both of these by dropping what the direction cannot
        // carry, and that is right for the sheet: it re-offers the destination and the category
        // when the direction flips, so the drop takes nothing the user still believes is set.
        // Every argument here was declared instead of offered, so the same drop answers "Created."
        // for a card no cycle will ever be charged to and a classification the template does not
        // have — and, for the card, a refusal naming the `account_id` nobody gave.
        if (type.isIncome && card != null) {
            return@writing refusedWith(
                AgentRefusal(
                    reason = "A card takes expenses only: with `type` income, give `account_id` " +
                        "and not `card_id`.",
                ),
                summary = summary,
            )
        }

        if (category != null && !category.type.isAccept(type)) {
            return@writing refusedWith(
                AgentRefusal(
                    reason = "`category_id` names \"${category.name}\", an " +
                        "${category.type.name.lowercase()} category, and `type` is " +
                        "${type.name.lowercase()}: a category classifies one direction only. " +
                        "Give an ${type.name.lowercase()} category, or leave `category_id` out.",
                ),
                summary = summary,
            )
        }

        saveRecurring(
            type = type,
            amount = amount.asFormAmount(),
            title = title,
            dayOfMonth = dayOfMonth.toString(),
            category = category,
            account = account,
            creditCard = card,
        ).reported(
            summary = summary,
            payload = {
                AgentRecurringWriteAnswer(
                    recurring = it.asAgentRecurring(),
                    note = "Created. Nothing is in the ledger until a cycle is confirmed.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.RECURRING, it.id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// update_recurring
// ----------------------------------------------------------------------------------

/**
 * **Edits a monthly template.**
 *
 * The same use case creating one writes this, with the identity of what is being edited — one door
 * for both, so a template cannot be born under one set of rules and edited under another.
 */
internal class UpdateRecurringTool(
    private val recurringRepository: IRecurringRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val categoryRepository: ICategoryRepository,
    private val saveRecurring: SaveRecurringUseCase,
) : McpTool {

    override val name: String = McpToolName.UPDATE_RECURRING.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Change a monthly template — its amount, title, day, category, or where it posts. " +
            "What is not given keeps the value it already has. " +
            "An income always goes to an account, so type income is refused while a card is in " +
            "play — the one named in card_id, or the one the template already posts to. A " +
            "category classifies one direction only, and an edit whose direction the category " +
            "cannot classify — the one given here, or the one the template already has — is " +
            "refused rather than dropping it: give a category_id that classifies the new " +
            "direction, or 0 for no category. " +
            "PERIMETER: it changes the template, never a cycle already confirmed. A confirmed " +
            "cycle is an ordinary posting from then on: edit it with update_transaction. " +
            "Archiving the template is archive_entity, which stops future cycles without " +
            "touching the ones already posted."

    override val inputSchema = schema(
        "id" to number("The template to edit, from list_recurring."),
        "type" to choice("Whether the cycle takes money out or brings it in.", RECURRING_TYPES.keys.toList()),
        "amount" to amount("The new amount of each cycle — 45.90, not 4590."),
        "day_of_month" to number("The day the cycle falls on, 1 to 31."),
        "title" to text("The new title."),
        "category_id" to number(
            "The category each cycle is classified under, from list_categories. Keeps the one " +
                "the template has when not given; pass 0 to leave it unclassified.",
        ),
        "account_id" to number("Post the cycle through this account, from list_accounts."),
        "card_id" to number("Charge the cycle to this card, from list_cards."),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")

        val stored = recurringRepository.getRecurringById(id)
            ?: return@writing refusedWith(
                AgentRefusal.notFound("recurring", id),
                summary = "edit recurring $id",
            )

        val namedAccount = arguments.long("account_id")
        val namedCard = arguments.long("card_id")

        val card = namedCard?.let { creditCardRepository.require(it) }?.takeIf { namedAccount == null }
            ?: stored.creditCard.takeIf { namedAccount == null }
        val account = namedAccount?.let { accountRepository.require(it) }
            ?: stored.account.takeIf { card == null }

        val amount = arguments.money("amount") ?: stored.amount
        val summary = "recurring ${stored.label}"

        val type = arguments.oneOf("type", RECURRING_TYPES.keys.toList())
            ?.let { RECURRING_TYPES.getValue(it) }
            ?: stored.type

        // What the direction cannot carry, `RecurringForm.toRecurring` drops — right for the
        // sheet, which re-offers both when the direction flips, and wrong here: most of what this
        // rewrite carries the call never named, so the same drop answers "Edited." for a card the
        // template no longer posts to and a classification it no longer has.
        if (type.isIncome && card != null) {
            return@writing refusedWith(
                AgentRefusal(
                    reason = "A card takes expenses only: with `type` income, give `account_id` " +
                        "and not `card_id`.",
                ),
                summary = summary,
            )
        }

        // Absent leaves the classification as it is, which is what every other field does here; a
        // `category_id` of 0 is how the call says the template has none, and the only way this
        // edit takes a classification away.
        val declaredCategory = arguments.long("category_id")
            ?.takeIf { it != NO_CATEGORY }
            ?.let { categoryRepository.require(it) }

        val carriedCategory = stored.category?.takeUnless { arguments.names("category_id") }

        if (declaredCategory != null && !declaredCategory.type.isAccept(type)) {
            return@writing refusedWith(
                AgentRefusal(
                    reason = "`category_id` names \"${declaredCategory.name}\", an " +
                        "${declaredCategory.type.name.lowercase()} category, and `type` is " +
                        "${type.name.lowercase()}: a category classifies one direction only. " +
                        "Give an ${type.name.lowercase()} category, or `category_id` 0 to leave " +
                        "the template unclassified.",
                ),
                summary = summary,
            )
        }

        // The same disagreement, reached from the other side: the category is the template's own
        // and the call said nothing about it, so what is wrong is not an argument but a
        // consequence the caller did not ask for and would only find by reading the template back.
        if (carriedCategory != null && !carriedCategory.type.isAccept(type)) {
            return@writing refusedWith(
                AgentRefusal(
                    reason = "The template is classified under \"${carriedCategory.name}\", an " +
                        "${carriedCategory.type.name.lowercase()} category, and `type` " +
                        "${type.name.lowercase()} cannot keep it: a category classifies one " +
                        "direction only. Give a `category_id` that classifies " +
                        "${type.name.lowercase()}, or `category_id` 0 to leave the template " +
                        "unclassified.",
                ),
                summary = summary,
            )
        }

        val category = declaredCategory ?: carriedCategory

        saveRecurring(
            id = id,
            type = type,
            amount = amount.asFormAmount(),
            title = arguments.string("title") ?: stored.title,
            dayOfMonth = (arguments.long("day_of_month") ?: stored.dayOfMonth.toLong()).toString(),
            category = category,
            account = account,
            creditCard = card,
            // Carried over, both of them: the anchor the cycle numbering is counted from is a
            // fact about when the template was created, and whether it is archived is not an
            // edit this tool makes.
            createdAt = stored.createdAt,
            isArchived = stored.isArchived,
        ).reported(
            summary = summary,
            payload = {
                AgentRecurringWriteAnswer(
                    recurring = it.asAgentRecurring(),
                    note = "Edited. Cycles already confirmed are untouched.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.RECURRING, id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// delete_recurring
// ----------------------------------------------------------------------------------

/** **Removes a template that was never used.** */
internal class DeleteRecurringTool(
    private val recurringRepository: IRecurringRepository,
    private val deleteRecurring: DeleteRecurringUseCase,
) : McpTool {

    override val name: String = McpToolName.DELETE_RECURRING.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Remove a monthly template for good. " +
            "PERIMETER: only a template that never posted can go. One whose cycles produced " +
            "transactions and one a budget uses as its base income are refused — the refusal " +
            "says which, and names archive_entity, which stops future cycles and keeps both the " +
            "template and the postings it already produced. A skipped cycle is not a posting and " +
            "does not stand in the way."

    override val inputSchema = schema(
        "id" to number("The template to remove, from list_recurring."),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")

        val stored = recurringRepository.getRecurringById(id)
            ?: return@writing refusedWith(
                AgentRefusal.notFound("recurring", id),
                summary = "delete recurring $id",
            )

        deleteRecurring(id).reported(
            summary = "recurring ${stored.label}",
            payload = {
                AgentRemovalAnswer(
                    removed = "recurring",
                    id = id,
                    name = stored.label,
                    note = "Removed. It had produced no posting, so the ledger is untouched.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.RECURRING, id) },
        )
    }
}
