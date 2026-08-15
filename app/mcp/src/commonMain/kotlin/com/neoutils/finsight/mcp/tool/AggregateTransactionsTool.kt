@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.mcp.contract.AssumedDefaults
import com.neoutils.finsight.mcp.contract.CivilDateRange
import com.neoutils.finsight.mcp.contract.ConsolidatedMoney
import com.neoutils.finsight.mcp.contract.DisplaySign
import com.neoutils.finsight.mcp.contract.McpTool
import com.neoutils.finsight.mcp.contract.MoneyByCurrencyPayload
import com.neoutils.finsight.mcp.contract.MoneyPayloadFactory
import com.neoutils.finsight.mcp.contract.ResponseLimits
import com.neoutils.finsight.mcp.contract.TOOL_NAME_PREFIX
import com.neoutils.finsight.mcp.contract.ToolAnnotations
import com.neoutils.finsight.mcp.contract.ToolError
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.ToolWarning
import com.neoutils.finsight.mcp.contract.asWarning
import com.neoutils.finsight.mcp.contract.refuseIfOversized
import com.neoutils.finsight.mcp.contract.toolOutcomeSchema
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The name of the aggregation tool, which the transaction listing names in its description. */
const val AGGREGATE_TRANSACTIONS_TOOL: String = "${TOOL_NAME_PREFIX}aggregate_transactions"

/** What an aggregate is broken down by. */
enum class AggregateGrouping {

    /** One line per category, plus the unclassified total the ledger answers under no dimension. */
    CATEGORY,

    /** One line per calendar month of the period. */
    MONTH,

    /** One line per account in scope. */
    ACCOUNT,

    /** One line per credit card in scope. */
    CARD,
}

/** The refusals this tool can produce beyond the common ones. */
internal object AggregateCodes {

    /** The period is required: an aggregate over all of history has no declared bound. */
    const val PERIOD_REQUIRED: String = "PERIOD_REQUIRED"

    /** The period ends before it starts. */
    const val INVALID_PERIOD: String = "INVALID_PERIOD"

    val all: Set<String> = setOf(PERIOD_REQUIRED, INVALID_PERIOD)
}

/**
 * Every total this surface offers, computed on the server over the **whole** period.
 *
 * It is the tool that protects the domain most. Without it a consumer pages the listing,
 * adds the amounts up and presents the result as exact — and it is not: adding outside the
 * server ignores that each account declares its own currency, and it counts as spending
 * what the domain does not classify as spending (a transfer between the user's own
 * accounts, the payment of a card bill, a reconciliation).
 *
 * **It does not paginate**, by construction: a total that arrived in pages would be a
 * total the consumer had to assemble, which is the very thing this exists to prevent. The
 * declared response limit applies here all the same, and a range that would exceed it is
 * refused with guidance on how to narrow it — never dumped.
 */
class AggregateTransactionsTool(
    private val entries: IEntryRepository,
    private val accounts: IAccountRepository,
    private val creditCards: ICreditCardRepository,
    private val categories: ICategoryRepository,
    private val money: MoneyPayloadFactory,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : McpTool {

    override val name: String = AGGREGATE_TRANSACTIONS_TOOL

    override val title: String = "Aggregate transactions"

    override val description: String = """
        Totals over a period, computed on the server over every transaction in it —
        by category, by month, by account or by card. This is the only correct way to
        obtain a total from this server; do not add up the pages of
        $LIST_TRANSACTIONS_TOOL.

        Every figure is a **collection of amounts, one per currency, and it stays a
        collection even when the user holds a single currency**. Beside it, when the
        archive has the rates for it, comes the same figure reduced to the base currency,
        carrying the rates that produced it. A missing rate is a warning and an absent
        consolidation, never a number.

        Spending reads negative and income positive.

        The scope defaults to every account the user holds; `accountIds` and
        `creditCardIds` narrow it, and `groupBy=CARD` scopes to the cards.

        There is no pagination here. A range whose answer would exceed the declared
        response size is refused, and the refusal says how to narrow it.
    """.trimIndent()

    override val inputSchema: JsonObject = objectSchema(required = listOf("startDate", "endDate")) {
        stringProperty("startDate", "Inclusive, YYYY-MM-DD. Required.")
        stringProperty("endDate", "Inclusive, YYYY-MM-DD. Required.")
        enumProperty(
            name = "groupBy",
            values = AggregateGrouping.entries.map { it.name },
            description = "How the total is broken down. CATEGORY by default.",
        )
        enumProperty(
            name = "nominalType",
            values = listOf("EXPENSE", "INCOME"),
            description = "Which side groupBy=CATEGORY totals. EXPENSE by default.",
        )
        arrayProperty(
            name = "accountIds",
            items = buildJsonObject { put("type", "integer") },
            description = "Narrows the scope to these accounts.",
        )
        arrayProperty(
            name = "creditCardIds",
            items = buildJsonObject { put("type", "integer") },
            description = "Narrows the scope to these cards.",
        )
    }

    override val outputSchema: JsonObject = toolOutcomeSchema(
        resultSchema = objectSchema(required = listOf("groupBy", "groups", "assumed")) {
            enumProperty("groupBy", AggregateGrouping.entries.map { it.name }, "How the total was broken down.")
            enumProperty("nominalType", listOf("EXPENSE", "INCOME"), "Which side a CATEGORY breakdown totalled.")
            arrayProperty(
                name = "groups",
                items = objectSchema {
                    objectProperty("category", refSchema("The category this line is about."))
                    booleanProperty("isUncategorized", "True on the line the unclassified total lands on.")
                    stringProperty("month", "YYYY-MM, on a MONTH breakdown.")
                    objectProperty("account", refSchema("The account this line is about."))
                    objectProperty("creditCard", refSchema("The card this line is about."))
                    objectProperty("total", moneyByCurrencySchema)
                    objectProperty("income", moneyByCurrencySchema)
                    objectProperty("expense", moneyByCurrencySchema)
                    objectProperty("balance", moneyByCurrencySchema)
                    objectProperty("openingBalance", moneyByCurrencySchema)
                },
                description = "One line per group. Figures are per-currency collections, never scalars.",
            )
            objectProperty("assumed", assumedSchema)
        },
        errorCodes = CommonToolCodes.all + AggregateCodes.all + ResponseLimits.CODE_RESPONSE_TOO_LARGE +
            AssumedDefaults.CODE_NOT_A_CIVIL_DATE,
    )

    override val annotations: ToolAnnotations = ToolAnnotations(readOnlyHint = true)

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        val args = Arguments(arguments)
        val startDate = args.date("startDate")
        val endDate = args.date("endDate")
        val grouping = args.enum("groupBy", AggregateGrouping.entries.toTypedArray()) ?: AggregateGrouping.CATEGORY
        val nominalType = args.enum("nominalType", arrayOf(AccountType.EXPENSE, AccountType.INCOME)) ?: AccountType.EXPENSE
        val accountIds = args.longs("accountIds")
        val creditCardIds = args.longs("creditCardIds")
        args.failure?.let { return ToolOutcome.Failed(it) }

        if (startDate == null || endDate == null) {
            return ToolOutcome.Failed(
                ToolError.invalidInput(
                    code = AggregateCodes.PERIOD_REQUIRED,
                    message = "`startDate` and `endDate` are both required: an aggregate is about a period, " +
                        "and one without bounds has no declared size.",
                ),
            )
        }

        if (endDate < startDate) {
            return ToolOutcome.Failed(
                ToolError.invalidInput(
                    code = AggregateCodes.INVALID_PERIOD,
                    message = "`endDate` ($endDate) is before `startDate` ($startDate)",
                ),
            )
        }

        val scopedAccounts = accounts.getAllAccountsIncludingClosed()
            .let { all -> accountIds?.let { ids -> all.filter { it.id in ids } } ?: all }
        val scopedCards = creditCards.getAllCreditCardsIncludingClosed()
            .let { all -> creditCardIds?.let { ids -> all.filter { it.id in ids } } ?: all }

        val assumed = AssumedDefaults.resolve(
            today = clock.today(timeZone),
            timeZone = timeZone,
            period = CivilDateRange(startDate, endDate),
        )

        val warnings = mutableListOf<ToolWarning>()
        val groups = when (grouping) {
            AggregateGrouping.CATEGORY -> byCategory(nominalType, startDate, endDate, scopedAccounts, scopedCards, creditCardIds, warnings)
            AggregateGrouping.MONTH -> byMonth(startDate, endDate, scopeIds(scopedAccounts, scopedCards, creditCardIds), warnings)
            AggregateGrouping.ACCOUNT -> scopedAccounts.map { account ->
                buildJsonObject {
                    putAccountRef("account", account)
                    putStats(entries.scopeStatsByCurrency(listOf(account.id), startDate, endDate), endDate, warnings)
                }
            }

            AggregateGrouping.CARD -> scopedCards.map { card ->
                buildJsonObject {
                    putRef("creditCard", card.id, card.name)
                    putStats(entries.scopeStatsByCurrency(listOf(card.accountId), startDate, endDate), endDate, warnings)
                }
            }
        }

        val outcome = ok(warnings = warnings.distinct()) {
            put("groupBy", grouping.name)
            if (grouping == AggregateGrouping.CATEGORY) put("nominalType", nominalType.name)
            put("groups", ToolJson.encodeToJsonElement(groups))
            putAssumed(assumed)
        }

        refuseIfOversized(
            bytes = sizeOf(outcome),
            howToNarrow = "request a shorter period, narrow the scope with `accountIds` or " +
                "`creditCardIds`, or group by MONTH instead of by CATEGORY",
        )?.let { return ToolOutcome.Failed(it, warnings = warnings.distinct()) }

        return outcome
    }

    /**
     * The scope, as identities of accounts in the chart: the user's accounts plus the
     * `LIABILITY` account of each card in scope.
     *
     * The cards join only when the call named them. Adding every card to the default
     * scope would make "what did I spend" include the settlement of a bill beside the
     * purchases that produced it.
     */
    private fun scopeIds(
        scopedAccounts: List<Account>,
        scopedCards: List<CreditCard>,
        creditCardIds: List<Long>?,
    ): List<Long> = scopedAccounts.map { it.id } +
        if (creditCardIds == null) emptyList() else scopedCards.map { it.accountId }

    private suspend fun byCategory(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        scopedAccounts: List<Account>,
        scopedCards: List<CreditCard>,
        creditCardIds: List<Long>?,
        warnings: MutableList<ToolWarning>,
    ): List<JsonObject> {
        val totals = entries.totalsByDimensionByCurrency(
            nominalType = nominalType,
            startDate = startDate,
            endDate = endDate,
            siblingAccountIds = scopeIds(scopedAccounts, scopedCards, creditCardIds) +
                // A category total is about what was classified, and a card purchase is
                // classified too — so the cards are always siblings here, unlike in the
                // scope of a balance.
                if (creditCardIds == null) creditCards.getAllCreditCardsIncludingClosed().map { it.accountId } else emptyList(),
        )

        val byDimension = categories.getAllCategoriesIncludingClosed().associateBy { it.dimensionId }
        val sign = DisplaySign.of(nominalType)

        return totals.entries
            .sortedBy { it.key ?: Long.MIN_VALUE }
            .map { (dimensionId, figure) ->
                val payload = money.spanning(figure, sign, endDate)
                payload.collect(warnings)
                buildJsonObject {
                    val category: Category? = dimensionId?.let(byDimension::get)
                    if (category != null) {
                        putRef("category", category.id, category.name)
                    } else {
                        // The `null` key of the ledger's own aggregate: nominal legs
                        // carrying no dimension. It is a group of the same read, which is
                        // why it can never diverge from the rest.
                        put("isUncategorized", true)
                    }
                    put("total", ToolJson.encodeToJsonElement(payload))
                }
            }
    }

    private suspend fun byMonth(
        startDate: LocalDate,
        endDate: LocalDate,
        scopeIds: List<Long>,
        warnings: MutableList<ToolWarning>,
    ): List<JsonObject> {
        val months = generateSequence(startDate.yearMonth) { it.plusMonth() }
            .takeWhile { it <= endDate.yearMonth }
            .toList()

        return months.map { month ->
            val from = maxOf(month.firstDay, startDate)
            val to = minOf(month.lastDay, endDate)
            buildJsonObject {
                put("month", month.toString())
                putStats(entries.scopeStatsByCurrency(scopeIds, from, to), to, warnings)
            }
        }
    }

    /**
     * The four figures a scope answers with, each per currency.
     *
     * `income` and `expense` arrive from the ledger as positive magnitudes, so the display
     * sign is applied by what the figure *is*: money arriving reads positive, money
     * leaving reads negative. `balance` and `openingBalance` are signed sums of the scope's
     * own legs and read as money held.
     */
    private suspend fun JsonObjectBuilder.putStats(
        stats: ScopeStatsByCurrency,
        on: LocalDate,
        warnings: MutableList<ToolWarning>,
    ) {
        val income = money.spanning(stats.income, DisplaySign.ofMoneyHeld, on)
        val expense = money.spanning(stats.expense, DisplaySign.of(AccountType.EXPENSE), on)
        val balance = money.spanning(stats.balance, DisplaySign.ofMoneyHeld, on)
        val opening = money.spanning(stats.openingBalance, DisplaySign.ofMoneyHeld, on)
        listOf(income, expense, balance, opening).forEach { it.collect(warnings) }

        put("income", ToolJson.encodeToJsonElement(income))
        put("expense", ToolJson.encodeToJsonElement(expense))
        put("balance", ToolJson.encodeToJsonElement(balance))
        put("openingBalance", ToolJson.encodeToJsonElement(opening))
    }
}

/**
 * Raises the warning a figure earns when it could not be reduced to one number.
 *
 * A missing rate is **not** an error: the per-currency figure is complete and the call
 * succeeded. What is missing is only its reduction, and saying so is the whole difference
 * between a partial result and a failure.
 */
internal fun MoneyByCurrencyPayload.collect(warnings: MutableList<ToolWarning>) {
    when (val figure = consolidated) {
        is ConsolidatedMoney.Unavailable -> warnings += figure.asWarning()
        else -> Unit
    }
}
