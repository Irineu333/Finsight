package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentAccount
import com.neoutils.finsight.mcp.surface.AgentAccountListAnswer
import com.neoutils.finsight.mcp.surface.AgentFigure
import com.neoutils.finsight.mcp.surface.AgentPeriod
import com.neoutils.finsight.mcp.surface.AgentPerimeter
import com.neoutils.finsight.mcp.surface.agentFigure
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * **Which accounts the user holds, what each is called, and what is in it.**
 *
 * This is how an agent turns "my savings account" into the identity every other tool takes, so the
 * names and the currencies matter as much as the figures. Each balance is **exact**, in that
 * account's own currency — one account is one currency, and nothing is converted to state it.
 *
 * The total is a ledger read over the same set, not the sum of the balances printed above it. Today
 * the two are the same number; they are the same number by *construction* only if one of them is
 * not arithmetic somebody could get wrong later.
 */
internal class ListAccountsTool(
    private val clock: Clock,
    private val accountRepository: IAccountRepository,
    private val calculateBalance: CalculateBalanceUseCase,
    private val consolidateMoney: ConsolidateMoneyUseCase,
) : McpTool {

    override val name: String = McpToolName.LIST_ACCOUNTS.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "Every account the user holds, with its identifier, its name, the currency it is " +
            "denominated in, and its balance at the end of a month. " +
            "PERIMETER: accounts only. Credit cards are NOT accounts here and are not in the " +
            "total — for those use list_cards, and for what is owned less what is owed, " +
            "get_net_worth. " +
            "Each balance is exact and in that account's own currency; `total` is the ledger's " +
            "figure over exactly the accounts listed, decomposed per currency when they differ. " +
            "Archived accounts are left out of both the list and the total unless asked for."

    override val inputSchema = schema(
        "month" to text(
            "The month the balances are taken at the end of, as `2026-03`. " +
                "Defaults to the month the app is in.",
        ),
        "include_archived" to yesOrNo("Include accounts the user has archived. Defaults to false."),
    )

    override suspend fun call(arguments: JsonObject?) = reading {
        val month = arguments.month("month", clock)
        val includeArchived = arguments.flag("include_archived", default = false)

        val accounts = if (includeArchived) {
            accountRepository.getAllAccountsIncludingClosed()
        } else {
            accountRepository.getAllAccounts()
        }

        // What the total leaves out is exactly what the list leaves out. The ledger's aggregate
        // spans every account of the nature, archived included, so the ones not listed are named
        // to it rather than subtracted from it afterwards.
        val excluded = accountRepository.getAllAccountsIncludingClosed()
            .filterNot { it in accounts }
            .map { it.id }
            .toSet()

        answer(
            AgentAccountListAnswer(
                asOf = AgentPeriod.upTo(month.lastDay, clock.today(), month.toString()),
                accounts = accounts.map { account ->
                    AgentAccount(
                        id = account.id,
                        name = account.name,
                        currency = account.currency,
                        // One account is one currency, and the ledger answered a number: exact,
                        // denominated by the account, never reduced to the base.
                        balance = AgentFigure.exact(
                            amount = calculateBalance.forAccount(account.id, month),
                            currency = account.currency,
                        ),
                        isDefault = account.isDefault,
                        isArchived = account.isArchived,
                        yieldsInterest = account.yieldsInterest,
                    )
                },
                total = consolidateMoney.agentFigure(
                    money = calculateBalance(target = month, excludedAccountIds = excluded),
                    on = month.lastDay,
                    policy = DisplayAmount::natural,
                ),
                perimeter = AgentPerimeter(
                    covers = "Every account listed here, counting every posting dated on or " +
                        "before ${month.lastDay}.",
                    excludes = listOfNotNull(
                        "credit cards, which are not accounts and hold debt rather than money",
                        "credit-card debt: it is NOT subtracted from `total`",
                        "archived accounts".takeUnless { includeArchived },
                    ),
                    seeAlso = listOf(
                        McpToolName.GET_BALANCE.wireName,
                        McpToolName.GET_NET_WORTH.wireName,
                        McpToolName.LIST_CARDS.wireName,
                    ),
                ),
            ),
        )
    }
}
