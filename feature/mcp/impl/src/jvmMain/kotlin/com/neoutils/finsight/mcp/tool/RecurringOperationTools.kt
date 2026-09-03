package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.ConfirmRecurringUseCase
import com.neoutils.finsight.domain.usecase.SkipRecurringUseCase
import com.neoutils.finsight.extension.isAccept
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentCycleAnswer
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.surface.AgentTransactionWriteAnswer
import com.neoutils.finsight.mcp.surface.toAgentTransaction
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import kotlinx.datetime.yearMonth
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * The two ways a cycle of a monthly template is handled: it happened, or it deliberately did not.
 *
 * Both are operations rather than registrations, and the distinction is worth keeping: a template is
 * not a posting, so until one of these runs there is nothing in the ledger and nothing to correct.
 */

/** The template a cycle belongs to, or the refusal that says which identity did not resolve. */
private suspend fun IRecurringRepository.require(recurringId: Long): Recurring =
    getRecurringById(recurringId)
        ?: throw BadArgument(AgentRefusal.notFound("recurring", recurringId))

// ----------------------------------------------------------------------------------
// confirm_recurring
// ----------------------------------------------------------------------------------

/**
 * **Confirms that one cycle of a template happened, posting it to the ledger.**
 *
 * ### Why this tool fills in the title and the category
 *
 * `ConfirmRecurringUseCase` resolves an omitted override in the body, and **not with one meaning**:
 * an omitted `amount`, `target`, `account` or `creditCard` asks for what the template describes,
 * while an omitted `title` or `category` asks for **nothing at all**. That asymmetry is right, and
 * it is right *for a screen*: both are things a user erases, the sheet arrives pre-filled from the
 * template, and re-substituting the template's value would hand back a name the user had just
 * deleted.
 *
 * A tool has no pre-filled form. Passing `null` through whenever the agent has no opinion would
 * confirm the Netflix cycle **with no title and no category**, and the agent would report "done"
 * with nothing to suggest otherwise. So this tool does what the sheet does: an unmentioned `title`
 * or `category_id` is filled in from the template and passed **explicitly**. That is
 * pre-filling — adaptation — and not a rule: the domain's meaning of `null` is untouched, and this
 * tool simply stops sending one by accident.
 *
 * Erasing stays expressible, because the sheet can erase: an empty `title`, or a `category_id` of
 * `0`, are the two ways of saying *this cycle genuinely had none*, and they are the only way a
 * `null` reaches the use case from here.
 */
internal class ConfirmRecurringTool(
    private val clock: Clock,
    private val recurringRepository: IRecurringRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val categoryRepository: ICategoryRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val confirmRecurring: ConfirmRecurringUseCase,
) : McpTool {

    override val name: String = McpToolName.CONFIRM_RECURRING.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Confirm that one cycle of a monthly template happened, posting it to the ledger. " +
            "PERIMETER: it posts one cycle and never edits the template — an amount, a title or " +
            "a category given here applies to this cycle alone, and next month still follows the " +
            "template. Changing the template itself is update_recurring; passing over a cycle is " +
            "skip_recurring. " +
            "Everything not given follows the template, including the title and the category. To " +
            "confirm a cycle that genuinely had neither, pass an empty title or a category_id of " +
            "0. A category classifies one direction only, and a cycle whose direction the " +
            "category cannot classify — the one given here, or the one the template already " +
            "carries — is refused rather than posted the other way round. Redirecting the cycle " +
            "to an account or card in another currency is refused rather than converted."

    override val inputSchema = schema(
        "id" to number("The template whose cycle this is, from list_recurring."),
        "date" to text(
            "The day the cycle is posted on, as `2026-03-14`, which also decides the month it " +
                "is filed under. Defaults to today.",
        ),
        "amount" to amount("What this cycle was actually worth, when it differs from the template's."),
        "title" to text(
            "What to call this cycle. Follows the template when not given; pass an empty string " +
                "for a cycle with no title of its own.",
        ),
        "category_id" to number(
            "How to classify this cycle, from list_categories. It must classify the template's " +
                "own direction. Follows the template when not given; pass 0 for a cycle with no " +
                "category.",
        ),
        "account_id" to number("Post this cycle to another account instead, from list_accounts."),
        "card_id" to number("Post this cycle to another card instead, from list_cards."),
        "invoice_month" to text(
            "Which invoice of the card this cycle lands on, as `2026-04` — the month it falls " +
                "due. Defaults to the card's open invoice, opened if it has none.",
        ),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")
        val recurring = recurringRepository.require(id)
        val date = arguments.date("date") ?: clock.today()

        val account = arguments.long("account_id")?.let { accountRepository.require(it) }
        val card = arguments.long("card_id")?.let { creditCardRepository.require(it) }

        if (account != null && card != null) {
            return@writing refusedWith(
                AgentRefusal(
                    reason = "A cycle posts to one place: give `account_id` or `card_id`, not both.",
                ),
                summary = "confirm ${recurring.label} for ${date.yearMonth}",
            )
        }

        // Pre-filled from the template, exactly as the app's own sheet arrives, and then passed
        // explicitly — see this class's KDoc for why `null` may not simply be forwarded.
        val title = arguments.stringOr("title", recurring.title)

        val declaredCategory = if (arguments.names("category_id")) {
            arguments.requiredLong("category_id")
                .takeIf { it != NO_CATEGORY }
                ?.let { categoryRepository.require(it) }
        } else {
            null
        }

        val carriedCategory = recurring.category.takeUnless { arguments.names("category_id") }

        // `ConfirmRecurringUseCase` refuses this, because a confirmation is the one write that
        // reaches the ledger with no form to drop what the direction cannot carry. The refusal is
        // repeated here for the same reason the other five tools repeat it: the domain answers
        // which combination is wrong, and only the tool can say which argument expresses it.
        if (declaredCategory != null && !declaredCategory.type.isAccept(recurring.type)) {
            return@writing refusedWith(
                AgentRefusal(
                    reason = "`category_id` names \"${declaredCategory.name}\", an " +
                        "${declaredCategory.type.name.lowercase()} category, and this is an " +
                        "${recurring.type.name.lowercase()} template: a category classifies one " +
                        "direction only. Give an ${recurring.type.name.lowercase()} category, or " +
                        "`category_id` 0 to confirm the cycle unclassified.",
                ),
                summary = "confirm ${recurring.label} for ${date.yearMonth}",
            )
        }

        // The same disagreement reached from the other side: the category is the template's own
        // and the call said nothing about it, so no argument is wrong and the refusal has to name
        // what the template holds. A template stored incoherent before the rule existed has no
        // migration, so this is reachable on data that is already there.
        if (carriedCategory != null && !carriedCategory.type.isAccept(recurring.type)) {
            return@writing refusedWith(
                AgentRefusal(
                    reason = "The template is classified under \"${carriedCategory.name}\", an " +
                        "${carriedCategory.type.name.lowercase()} category, and it is an " +
                        "${recurring.type.name.lowercase()} template: a category classifies one " +
                        "direction only. Give a `category_id` that classifies " +
                        "${recurring.type.name.lowercase()}, or `category_id` 0 to confirm this " +
                        "cycle unclassified. Correcting the template itself is update_recurring.",
                ),
                summary = "confirm ${recurring.label} for ${date.yearMonth}",
            )
        }

        val category = declaredCategory ?: carriedCategory

        val target = when {
            account != null -> TransactionTarget.ACCOUNT
            card != null -> TransactionTarget.CREDIT_CARD
            // Neither was named, so the template's own destination answers — which is what the
            // use case does with `null`, and the one absence it is right to forward.
            else -> null
        }

        val invoice = arguments.monthOrNull("invoice_month")?.let { month ->
            val onCard = card ?: recurring.creditCard
            onCard?.let { invoiceRepository.getInvoicesByCreditCard(it.id) }
                ?.firstOrNull { it.dueMonth == month }
                ?: throw BadArgument(
                    AgentRefusal(reason = "That card has no invoice falling due in $month."),
                )
        }

        confirmRecurring(
            recurringId = recurring.id,
            date = date,
            amount = arguments.money("amount"),
            target = target,
            account = account,
            creditCard = card,
            invoice = invoice,
            title = title,
            category = category,
        ).reported(
            summary = "${recurring.label} for ${date.yearMonth}",
            payload = { transaction ->
                AgentTransactionWriteAnswer(
                    transaction = transaction.toAgentTransaction(
                        lookup = TransactionFacadeLookup.of(
                            categories = listOfNotNull(category),
                            installments = emptyList(),
                        ),
                    )!!,
                    note = "Confirmed for ${date.yearMonth}. The template is unchanged, and the " +
                        "month stops being offered as pending.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.TRANSACTION, it.id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// skip_recurring
// ----------------------------------------------------------------------------------

/**
 * **Records that one cycle was deliberately passed over.**
 *
 * A skip writes no posting and produces no entry: it exists so the month stops being offered, and
 * so that the pass is a fact rather than the absence of one. The date decides which month the
 * occurrence is filed under, which is the whole content of the decision.
 */
internal class SkipRecurringTool(
    private val clock: Clock,
    private val recurringRepository: IRecurringRepository,
    private val skipRecurring: SkipRecurringUseCase,
) : McpTool {

    override val name: String = McpToolName.SKIP_RECURRING.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Record that one cycle of a monthly template was deliberately passed over. " +
            "PERIMETER: it writes no posting and moves no money — the month simply stops being " +
            "offered as pending, and the pass becomes a fact instead of an absence. The template " +
            "keeps generating: stopping it for good is archive_entity. A cycle already confirmed " +
            "cannot be skipped, because the money moved."

    override val inputSchema = schema(
        "id" to number("The template whose cycle is being passed over, from list_recurring."),
        "date" to text(
            "A day in the month being passed over, as `2026-03-14`. Defaults to today. It is " +
                "what decides which month the skip is filed under.",
        ),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")
        val recurring = recurringRepository.require(id)
        val date = arguments.date("date") ?: clock.today()

        skipRecurring(recurring.id, date).reported(
            summary = "${recurring.label} skipped for ${date.yearMonth}",
            payload = {
                AgentCycleAnswer(
                    recurring = recurring.asAgentRecurring(),
                    month = date.yearMonth,
                    note = "Passed over. Nothing was posted, and the template still generates " +
                        "from next month.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.RECURRING, recurring.id) },
        )
    }
}
