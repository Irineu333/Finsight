package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentAccount
import com.neoutils.finsight.mcp.surface.AgentBalanceAnswer
import com.neoutils.finsight.mcp.surface.AgentFigure
import com.neoutils.finsight.mcp.surface.AgentPeriod
import com.neoutils.finsight.mcp.surface.AgentPerimeter
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.surface.agentFigure
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * **How much money is in the accounts** — and, said in the same breath, that card debt is not
 * subtracted from it.
 *
 * The perimeter is stated twice on purpose, in [description] and again in every answer. A total of
 * account balances and a net worth are different numbers that look identical, and in the simulation
 * this surface was designed against an agent spent two calls working out which one it had. The worse
 * outcome is the one where it does not check.
 */
internal class GetBalanceTool(
    private val clock: Clock,
    private val calculateBalance: CalculateBalanceUseCase,
    private val consolidateMoney: ConsolidateMoneyUseCase,
    private val accountRepository: IAccountRepository,
) : McpTool {

    override val name: String = McpToolName.GET_BALANCE.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "How much money the user holds in their accounts, as of the end of a month. " +
            "PERIMETER: this is the sum of account balances only. Credit-card debt is NOT " +
            "subtracted from it, and money owed on a card is not part of it — for what is owned " +
            "less what is owed, call get_net_worth. " +
            "Give `account_id` for one account's balance instead of the total — exact, and in " +
            "that account's own currency. " +
            "A total spanning accounts in different currencies comes back decomposed per " +
            "currency, with the consolidated figure and the date of the rate that produced it."

    override val inputSchema = schema(
        "month" to text(
            "The month the balance is taken at the end of, as `2026-03`. " +
                "Defaults to the month the app is in.",
        ),
        "account_id" to number(
            "Scopes the answer to one account. Omit for the total across every account.",
        ),
        "exclude_account_ids" to numbers(
            "Accounts to leave out of the total. Ignored when `account_id` is given.",
        ),
    )

    override suspend fun call(arguments: JsonObject?) = reading {
        val month = arguments.month("month", clock)
        val today = clock.today()
        val accountId = arguments.long("account_id")

        if (accountId != null) {
            val account = accountRepository.getAccountById(accountId)
                ?: return@reading refused(AgentRefusal.notFound("account", accountId))

            val balance = calculateBalance.forAccount(accountId, month)
            return@reading answer(
                AgentBalanceAnswer(
                    // One account is one currency, and the ledger answered a number: exact, in the
                    // currency the account declares, never reduced to the base.
                    balance = AgentFigure.exact(balance, account.currency),
                    account = AgentAccount(
                        id = account.id,
                        name = account.name,
                        currency = account.currency,
                        isDefault = account.isDefault,
                        isArchived = account.isArchived,
                        yieldsInterest = account.yieldsInterest,
                    ),
                    asOf = AgentPeriod.upTo(month.lastDay, today, month.toString()),
                    perimeter = AgentPerimeter(
                        covers = "Every posting on `${account.name}` dated on or before " +
                            "${month.lastDay}, in ${account.currency}.",
                        excludes = listOf(
                            "the user's other accounts — this is one account, not the total",
                            "credit-card debt, which is owed on the card and not on an account",
                        ),
                        seeAlso = listOf(
                            McpToolName.GET_BALANCE.wireName,
                            McpToolName.GET_NET_WORTH.wireName,
                        ),
                    ),
                )
            )
        }

        val excluded = arguments.longs("exclude_account_ids").orEmpty().toSet()
        val money = calculateBalance(target = month, excludedAccountIds = excluded)

        answer(
            AgentBalanceAnswer(
                balance = consolidateMoney.agentFigure(
                    money = money,
                    on = month.lastDay,
                    policy = DisplayAmount::natural,
                ),
                asOf = AgentPeriod.upTo(month.lastDay, today, month.toString()),
                perimeter = AgentPerimeter(
                    covers = "The balance of every account the user holds, each currency with " +
                        "its own, counting every posting dated on or before ${month.lastDay}" +
                        if (excluded.isEmpty()) "." else ", except the accounts left out.",
                    excludes = listOf(
                        "credit-card debt: what is owed on a card is NOT subtracted here",
                        "amounts spent on a card and not yet paid",
                    ),
                    seeAlso = listOf(
                        McpToolName.GET_NET_WORTH.wireName,
                        McpToolName.GET_CARD_OVERVIEW.wireName,
                    ),
                ),
            )
        )
    }
}
