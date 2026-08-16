package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.form.CreditCardForm
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.usecase.AddCreditCardUseCase
import com.neoutils.finsight.domain.usecase.DeleteCreditCardUseCase
import com.neoutils.finsight.domain.usecase.UpdateCreditCardUseCase
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentCard
import com.neoutils.finsight.mcp.surface.AgentCardWriteAnswer
import com.neoutils.finsight.mcp.surface.AgentFigure
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.surface.AgentRemovalAnswer
import com.neoutils.finsight.util.AppIcon
import kotlinx.serialization.json.JsonObject

/** A card as an agent receives it back from a write — no usage figure, because none was read. */
private fun CreditCard.asAgentCard() = AgentCard(
    id = id,
    name = name,
    currency = currency,
    closingDay = closingDay,
    dueDay = dueDay,
    limit = currency?.let { AgentFigure.exact(limit, it) },
    isArchived = isArchived,
)

// ----------------------------------------------------------------------------------
// create_card
// ----------------------------------------------------------------------------------

/**
 * **Creates one of the user's cards, with its first invoice already open.**
 *
 * Opening that invoice is part of the creation and not a follow-up to it — `AddCreditCardUseCase`
 * fails the whole creation when it cannot, because a card whose cycle never opened would accept no
 * expense at all. The tool does not ask for the cycle and does not open one.
 */
internal class CreateCardTool(
    private val addCreditCard: AddCreditCardUseCase,
) : McpTool {

    override val name: String = McpToolName.CREATE_CARD.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Create one of the user's credit cards. Its first invoice is opened with it, on the " +
            "cycle the card is already in today — there is no separate step for that. " +
            "The currency is required and fixed from this moment on: every figure of the card " +
            "is denominated in it. " +
            "PERIMETER: closing_day and due_day describe the cycle, and both are days of the " +
            "month between 1 and 31. The limit is what the bank granted, not what is owed."

    override val inputSchema = schema(
        "name" to text("What the user calls it. Must not clash with a card that already exists."),
        "limit" to amount("What the bank granted, in the card's own currency — 5000.00, not 500000."),
        "closing_day" to number("The day of the month the invoice closes on, 1 to 31."),
        "due_day" to number("The day of the month the invoice falls due on, 1 to 31."),
        "currency" to text("The ISO code the card is denominated in, as `BRL`. Fixed from now on."),
        required = listOf("name", "limit", "closing_day", "due_day", "currency"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val name = arguments.requiredString("name")
        val limit = arguments.requiredMoney("limit")
        val currency = arguments.requiredString("currency").uppercase()

        val form = CreditCardForm(
            name = name,
            limit = limit.asFormAmount(),
            closingDayUser = arguments.requiredLong("closing_day").toString(),
            dueDayUser = arguments.requiredLong("due_day").toString(),
            iconKey = AppIcon.CARD.key,
        )

        addCreditCard(form, currency).reported(
            summary = "card $name in $currency",
            payload = {
                AgentCardWriteAnswer(
                    // The card the use case answers with is the one it stored, before the
                    // currency is hydrated off its `LIABILITY` account on the next read; the
                    // currency it was created in is the one stated here.
                    card = it.asAgentCard().copy(
                        currency = currency,
                        limit = AgentFigure.exact(it.limit, currency),
                    ),
                    note = "Created, with its first invoice open on the cycle it is in today.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.CREDIT_CARD, it.id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// update_card
// ----------------------------------------------------------------------------------

/**
 * **Edits a card.**
 *
 * The currency is absent from the schema, and not because it is guarded: a `CreditCard` carries no
 * currency field to name — it is denominated by the `LIABILITY` account it projects onto — so the
 * whole class of error is unutterable here rather than refused.
 */
internal class UpdateCardTool(
    private val creditCardRepository: ICreditCardRepository,
    private val updateCreditCard: UpdateCreditCardUseCase,
) : McpTool {

    override val name: String = McpToolName.UPDATE_CARD.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Change a card's name, its limit, or the days its cycle closes and falls due on. " +
            "What is not given keeps the value it already has. " +
            "PERIMETER: a card is denominated by the account it settles on and has no currency " +
            "of its own to change. Moving the cycle does not move the invoices already open — " +
            "it decides the ones opened from now on. Archiving is archive_entity."

    override val inputSchema = schema(
        "id" to number("The card to edit, from list_cards."),
        "name" to text("The new name."),
        "limit" to amount("The new limit, in the card's own currency — 5000.00, not 500000."),
        "closing_day" to number("The day of the month the invoice closes on, 1 to 31."),
        "due_day" to number("The day of the month the invoice falls due on, 1 to 31."),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")
        val stored = creditCardRepository.require(id)

        val name = arguments.string("name")
        val limit = arguments.money("limit")
        val closingDay = arguments.long("closing_day")?.toInt()
        val dueDay = arguments.long("due_day")?.toInt()

        updateCreditCard(id) { card ->
            card.copy(
                name = name ?: card.name,
                limit = limit ?: card.limit,
                closingDay = closingDay ?: card.closingDay,
                dueDay = dueDay ?: card.dueDay,
            )
        }.reported(
            summary = "card ${stored.name}",
            payload = {
                AgentCardWriteAnswer(
                    card = it.asAgentCard().copy(currency = stored.currency),
                    note = "Edited. Everything the call did not name kept the value it had.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.CREDIT_CARD, it.id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// delete_card
// ----------------------------------------------------------------------------------

/** **Removes a card that never moved, facade and ledger account together.** */
internal class DeleteCardTool(
    private val creditCardRepository: ICreditCardRepository,
    private val deleteCreditCard: DeleteCreditCardUseCase,
) : McpTool {

    override val name: String = McpToolName.DELETE_CARD.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Remove a card for good, together with the ledger account behind it. " +
            "PERIMETER: only a card that never moved can go. One with postings and one a " +
            "recurring template still points at are refused — deleting the second would leave " +
            "the template silently pointing at an account instead of a card. The refusal names " +
            "archive_entity, which keeps the card and its invoices out of every selector."

    override val inputSchema = schema(
        "id" to number("The card to remove, from list_cards."),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")

        val stored = creditCardRepository.getCreditCardById(id)
            ?: return@writing refusedWith(
                AgentRefusal.notFound("card", id),
                summary = "delete card $id",
            )

        deleteCreditCard(id).reported(
            summary = "card ${stored.name}",
            payload = {
                AgentRemovalAnswer(
                    removed = "card",
                    id = id,
                    name = stored.name,
                    alsoRemoved = listOf("the ledger account the card projected onto"),
                    note = "Removed. It had no movement, so nothing in the ledger went with it.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.CREDIT_CARD, id) },
        )
    }
}
