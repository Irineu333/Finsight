package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.matches
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.deriveTransactionLabel
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentListingTotals
import com.neoutils.finsight.mcp.surface.AgentPeriod
import com.neoutils.finsight.mcp.surface.AgentPerimeter
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.surface.AgentTransactionListAnswer
import com.neoutils.finsight.mcp.surface.agentFigure
import com.neoutils.finsight.mcp.surface.toAgentTransaction
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import com.neoutils.finsight.ui.model.TransactionPerspective
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * **The postings of a month**, cut by whatever the caller named, in pages, with the ledger's own
 * totals beside them.
 *
 * Three things it does that a naive listing gets wrong, and each of them is a requirement rather
 * than a nicety:
 *
 * - **The totals are not the page's.** Fifty postings of a hundred and twenty-seven come back and
 *   the figures are the hundred and twenty-seven's, because they are read from the ledger over the
 *   filter and never summed from what was returned. `matching` and `returned` are what say so.
 * - **The vocabulary changes with the point of view.** With no account named there is nobody to
 *   see the movement from, so each posting carries the **nature** the ledger derives — a transfer
 *   is a transfer. Name an account or a card and each carries the **direction** seen from there,
 *   and the same transfer is an outflow of one end. Reporting a direction without a point of view
 *   is how transfers end up counted as spending.
 * - **The order is total.** A date has a day's resolution, so the identity the ledger assigns
 *   breaks every tie; and "the last thing I recorded" is a question the date cannot answer at all,
 *   which is why `order_by` offers the recording order as a criterion of its own.
 */
internal class ListTransactionsTool(
    private val clock: Clock,
    private val transactionRepository: ITransactionRepository,
    private val entryRepository: IEntryRepository,
    private val accountRepository: IAccountRepository,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val installmentRepository: IInstallmentRepository,
    private val consolidateMoney: ConsolidateMoneyUseCase,
    /**
     * The tie-break between the two ends of a cross-currency operation, read live.
     *
     * Nothing is converted by it and it is never a fallback: an operation that crossed currencies
     * has two exact figures, both the ledger's own, and with no account named this decides which of
     * them is stated — the same choice `Transaction.figureLegUnder` makes for the app's own neutral
     * lists, so a posting cannot read as one figure on the screen and another to the agent.
     */
    private val baseCurrency: () -> String,
) : McpTool {

    override val name: String = McpToolName.LIST_TRANSACTIONS.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "The postings of one month, newest first, in pages — with the totals the ledger gives " +
            "for the same filter. " +
            "PERIMETER: `matching` is how many postings the filter reaches and `returned` how " +
            "many came back; `totals` is read from the ledger and never summed from this page. " +
            "It covers all the matching postings EXCEPT where `nature` narrows them — the ledger " +
            "has no total cut that way, so `nature` cuts the list and leaves the totals where " +
            "they are. `narrowed_by` names exactly the arguments the totals do reflect; read it " +
            "before quoting them beside a narrowed list. Transfers between the user's own " +
            "accounts and credit-card payments appear in the list and are in NEITHER total, " +
            "because neither is income or spending. " +
            "Give `account_id` or `card_id` — never both — to read the month from that account " +
            "or card: each posting then carries `direction` (which way the money went, seen from " +
            "there). Without one, each carries `nature` instead, and a transfer is a transfer " +
            "rather than an expense. " +
            "`order_by=recorded` answers \"the last thing I entered\", which the date cannot: it " +
            "has a day's resolution. Both orders are total, so paging repeats nothing and drops " +
            "nothing."

    override val inputSchema = schema(
        "month" to text("The month, as `2026-03`. Defaults to the month the app is in."),
        "account_id" to number("Read the month from this account. Not with `card_id`."),
        "card_id" to number("Read the month from this credit card. Not with `account_id`."),
        "category_id" to number("Only the postings classified under this category."),
        "nature" to choice(
            "Only the postings of this nature, as the ledger derives it.",
            NATURES.keys.toList(),
        ),
        "order_by" to choice(
            "How to order the page. `date` is the day of the posting; `recorded` is the order " +
                "they were entered in, which is what \"the last one I registered\" means.",
            ListingOrder.wireNames,
        ),
        "limit" to number("How many postings to return. Defaults to $DEFAULT_LIMIT, at most $MAX_LIMIT."),
        "offset" to number("How many to skip — the page after the last is `offset + returned`."),
    )

    override suspend fun call(arguments: JsonObject?) = reading {
        val month = arguments.month("month", clock)
        val today = clock.today()
        val order = ListingOrder.of(arguments.oneOf("order_by", ListingOrder.wireNames))
        val offset = arguments.count("offset", default = 0, max = MAX_OFFSET)
        val limit = arguments.count("limit", default = DEFAULT_LIMIT, max = MAX_LIMIT, min = 1)
        val label = arguments.oneOf("nature", NATURES.keys.toList())?.let { NATURES.getValue(it) }

        val accountId = arguments.long("account_id")
        val cardId = arguments.long("card_id")
        val categoryId = arguments.long("category_id")

        if (accountId != null && cardId != null) {
            return@reading refused(
                AgentRefusal(
                    reason = "A listing is read from one point of view, so `account_id` and " +
                        "`card_id` cannot both be given. Ask twice, or drop one.",
                ),
            )
        }

        var account: Account? = null
        if (accountId != null) {
            account = accountRepository.getAccountById(accountId)
                ?: return@reading refused(AgentRefusal.notFound("account", accountId))
        }

        var card: CreditCard? = null
        if (cardId != null) {
            card = creditCardRepository.getCreditCardById(cardId)
                ?: return@reading refused(AgentRefusal.notFound("credit card", cardId))
        }

        var category: Category? = null
        if (categoryId != null) {
            category = categoryRepository.getCategoryById(categoryId)
                ?: return@reading refused(AgentRefusal.notFound("category", categoryId))
        }

        // The card enters as its `LIABILITY` account: "a posting of card X" *is* "a posting with a
        // leg on X's account", and the ledger has no other way to say it.
        val perspective = account?.id ?: card?.accountId

        val matching = transactionRepository.getAllTransactions()
            .filter { it.date.yearMonth == month }
            .filter { perspective == null || it.entries.any { leg -> leg.account.id == perspective } }
            // Decided by the single owner of what a value of the analytic axis contains, so this
            // cuts by the same rule as the one that cuts the ledger.
            .filter { category == null || it.matches(SpendingSubject.Categorized(category)) }
            .filter { label == null || it.entries.deriveTransactionLabel() == label }
            .inOrder(order)

        val page = matching.pageOf(offset = offset, limit = limit)

        // The facades the ledger hands out identities for: a posting carries the dimension its
        // nominal leg is classified by and the id of its instalment, and naming either belongs to
        // whoever owns that facade. Gathered once for the page, never once per row.
        val lookup = TransactionFacadeLookup.of(
            categories = categoryRepository.getAllCategoriesIncludingClosed(),
            installments = installmentRepository.getAllInstallments(),
        )

        answer(
            AgentTransactionListAnswer(
                period = AgentPeriod.of(month, today),
                matching = page.matching,
                returned = page.returned,
                offset = page.offset,
                hasMore = page.hasMore,
                orderedBy = order.wireName,
                readFrom = account?.name ?: card?.name,
                totals = totals(month, perspective, category),
                transactions = page.items.mapNotNull {
                    it.toAgentTransaction(
                        perspective = perspective?.let(::TransactionPerspective),
                        lookup = lookup,
                        baseCurrency = baseCurrency(),
                    )
                },
                perimeter = perimeter(month, account, card, category, label),
            ),
        )
    }

    // ------------------------------------------------------------------------------
    // The totals, from the ledger, for the filter
    // ------------------------------------------------------------------------------

    /**
     * The money the filter comes to — **read**, never accumulated.
     *
     * Three filters, three ledger aggregates, each of them the one already parameterised by exactly
     * what was asked. None of them looks at the postings this call is about to return, which is the
     * point: the answer is the same whether the caller took the first page or the fourth.
     */
    private suspend fun totals(
        month: YearMonth,
        perspective: Long?,
        category: Category?,
    ): AgentListingTotals {
        val on = month.lastDay

        suspend fun figure(money: MoneyByCurrency) = consolidateMoney.agentFigure(
            money = money,
            on = on,
            policy = DisplayAmount::magnitude,
        )

        val narrowedBy = listOfNotNull(
            "month",
            "account_id or card_id".takeIf { perspective != null },
            "category_id".takeIf { category != null },
        )

        if (category != null) {
            // The category's own total over the same perimeter: Σ the nominal legs carrying its
            // dimension, on the transactions that also touch the accounts being read from. It is
            // exactly what the returned postings are classified by, and it is one read.
            val money = entryRepository.totalsByDimensionByCurrency(
                nominalType = category.nominalType,
                startDate = month.firstDay,
                endDate = month.lastDay,
                siblingAccountIds = perspective?.let(::listOf) ?: monetaryAccountIds(),
            )[category.dimensionId] ?: MoneyByCurrency.zero

            val figure = figure(money)
            val nothing = figure(MoneyByCurrency.zero)

            return AgentListingTotals(
                income = if (category.type.isIncome) figure else nothing,
                expense = if (category.type.isExpense) figure else nothing,
                basis = "The ledger's total for the category `${category.name}` over this month — " +
                    "the sum of the legs classified under it, not of the postings returned here.",
                narrowedBy = narrowedBy,
            )
        }

        if (perspective != null) {
            // The same read a report of one account or one card comes through: money classified as
            // it crosses that account's own boundary. A transfer out of it counts as an outflow,
            // because from there it is one.
            val stats = entryRepository.scopeStatsByCurrency(
                scopeAccountIds = listOf(perspective),
                startDate = month.firstDay,
                endDate = month.lastDay,
            )

            return AgentListingTotals(
                income = figure(stats.income),
                expense = figure(stats.expense),
                basis = "The ledger's income and expense for this account or card over the " +
                    "month: every posting that crossed its boundary, counted where it crossed. " +
                    "Money moved to another of the user's own accounts is an outflow here, " +
                    "because from this side it is one.",
                narrowedBy = narrowedBy,
            )
        }

        val asset = entryRepository.assetMonthFlowsByCurrency(month)
        val liability = entryRepository.liabilityMonthFlowsByCurrency(month)

        return AgentListingTotals(
            income = figure(asset.income),
            // Disjoint sets — a card purchase has no account leg — so adding them cannot
            // double-count, and `MoneyByCurrency.plus` is the one implementation of that addition.
            expense = figure(asset.expense + liability.expense),
            basis = "The ledger's month-wide flows across every account and every card. Transfers " +
                "between the user's own accounts and credit-card payments are excluded by the " +
                "read itself: neither brings money in or takes it out.",
            narrowedBy = narrowedBy,
        )
    }

    /** Every account money can sit in — the perimeter of "the whole month", in the chart's terms. */
    private suspend fun monetaryAccountIds(): List<Long> = accountRepository
        .getAllLedgerAccounts()
        .filter { it.type.isMonetary }
        .map { it.id }

    /**
     * Which nominal legs a category's total is made of.
     *
     * The declaration is the user's — nothing in the ledger produces "this is an expense category"
     * — and this is the one place the catalogue turns it into the account nature the read wants.
     */
    private val Category.nominalType: AccountType
        get() = if (type.isExpense) AccountType.EXPENSE else AccountType.INCOME

    // ------------------------------------------------------------------------------

    private fun perimeter(
        month: YearMonth,
        account: Account?,
        card: CreditCard?,
        category: Category?,
        label: TransactionLabel?,
    ): AgentPerimeter {
        val readFrom = account?.name ?: card?.name

        return AgentPerimeter(
            covers = buildString {
                append("Every posting dated within $month")
                readFrom?.let { append(" with a leg on `$it`") }
                category?.let { append(" classified under `${it.name}`") }
                label?.let { append(" whose nature is `${it.name.lowercase()}`") }
                append(". `totals` is the ledger's figure for the same filter, over all ")
                append("`matching` postings rather than over the page returned.")
            },
            excludes = listOfNotNull(
                "postings dated outside $month",
                "transfers between the user's own accounts and credit-card payments — listed, " +
                    "but in neither total, because neither is income or spending",
                label?.let {
                    "nothing else from `totals`: `nature` cuts the list, and the ledger has no " +
                        "aggregate cut the same way, so the two figures stay the perimeter's"
                },
                "anything a page after this one holds — `has_more` says whether there is one",
            ),
            seeAlso = listOf(
                McpToolName.GET_TRANSACTION.wireName,
                McpToolName.GET_MONTH_SUMMARY.wireName,
                McpToolName.GET_SPENDING_BREAKDOWN.wireName,
            ),
        )
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 200
        const val MAX_OFFSET = 100_000
    }
}
