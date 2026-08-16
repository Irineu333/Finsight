package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.domain.usecase.Limit
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentCard
import com.neoutils.finsight.mcp.surface.AgentCardListAnswer
import com.neoutils.finsight.mcp.surface.AgentFigure
import com.neoutils.finsight.mcp.surface.AgentPerimeter
import kotlinx.serialization.json.JsonObject

/**
 * **Which cards the user holds, what each is called, and when each one closes and falls due.**
 *
 * Its recorte, against `get_card_overview`: this is the **catalogue** — the identities and the
 * calendar, which is what an agent needs to turn "the Nubank card" into the identifier every other
 * tool takes. It says nothing about which invoice is open right now, and it enumerates no invoice
 * at all. `get_card_overview` is the one that does.
 *
 * The limit figures come along because they are the card's own standing facts and cost one read for
 * all of them (design D7). They are three separate readings and not two plus a subtraction: what is
 * available is what the app computes, and an agent handed `limit` and `used` alone would invent the
 * third.
 */
internal class ListCardsTool(
    private val creditCardRepository: ICreditCardRepository,
    private val calculateAvailableLimit: CalculateAvailableLimitUseCase,
) : McpTool {

    override val name: String = McpToolName.LIST_CARDS.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "Every credit card the user holds, with its identifier, its name, the currency it is " +
            "denominated in, the day it closes, the day it falls due, and its limit. " +
            "PERIMETER: this is the catalogue of cards — identities and the calendar. It names " +
            "NO invoice: for the one open right now and what all unpaid invoices owe together " +
            "use get_card_overview, and for a card's invoices over time use list_invoices. " +
            "`used`, `available` and `limit` are three separate readings, not two plus a " +
            "subtraction. Figures are in each card's own currency and are never added across " +
            "cards: doing that is conversion, and no total here is one."

    override val inputSchema = schema(
        "include_archived" to yesOrNo("Include cards the user has archived. Defaults to false."),
    )

    override suspend fun call(arguments: JsonObject?) = reading {
        val includeArchived = arguments.flag("include_archived", default = false)

        val cards = if (includeArchived) {
            creditCardRepository.getAllCreditCardsIncludingClosed()
        } else {
            creditCardRepository.getAllCreditCards()
        }

        // One read for every card, not one per card: the plural form is the canonical one precisely
        // because a list is what asks this question.
        val limits = calculateAvailableLimit(cards.map { it.id })

        answer(
            AgentCardListAnswer(
                cards = cards.map { it.toAgentCard(limits[it.id] ?: Limit.NONE) },
                perimeter = AgentPerimeter(
                    covers = "Every credit card the user holds, with its limit and how much of " +
                        "it the unpaid invoices are holding.",
                    excludes = listOfNotNull(
                        "invoices — this list names none of them",
                        "spending on a card that has not yet landed on an invoice",
                        "any total across cards: each figure stays in its card's own currency",
                        "archived cards".takeUnless { includeArchived },
                    ),
                    seeAlso = listOf(
                        McpToolName.GET_CARD_OVERVIEW.wireName,
                        McpToolName.LIST_INVOICES.wireName,
                    ),
                ),
            ),
        )
    }

    private fun CreditCard.toAgentCard(limit: Limit) = AgentCard(
        id = id,
        name = name,
        currency = currency,
        closingDay = closingDay,
        dueDay = dueDay,
        limit = figure(this.limit),
        used = figure(limit.totalUnpaidAmount),
        available = figure(limit.available),
        isArchived = isArchived,
    )

    /**
     * A card's figure, in the card's own currency.
     *
     * `null` when the card carries no currency — an instance that never came from a hydrated read.
     * A default here would denominate somebody's money by guessing, which is the one thing the
     * money types in this app exist to make impossible.
     */
    private fun CreditCard.figure(amount: Double): AgentFigure? =
        currency?.let { AgentFigure.exact(amount, it) }
}
