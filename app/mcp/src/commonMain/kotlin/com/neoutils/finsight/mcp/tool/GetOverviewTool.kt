@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.mcp.contract.ArchivedScope
import com.neoutils.finsight.mcp.contract.AssumedDefaults
import com.neoutils.finsight.mcp.contract.DisplaySign
import com.neoutils.finsight.mcp.contract.McpTool
import com.neoutils.finsight.mcp.contract.MoneyAmount
import com.neoutils.finsight.mcp.contract.MoneyPayloadFactory
import com.neoutils.finsight.mcp.contract.TOOL_NAME_PREFIX
import com.neoutils.finsight.mcp.contract.ToolAnnotations
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.ToolWarning
import com.neoutils.finsight.mcp.contract.toolOutcomeSchema
import kotlinx.datetime.TimeZone
import kotlinx.datetime.yearMonth
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The name of the entry point of this surface, named by the resources and the prompts. */
const val GET_OVERVIEW_TOOL: String = "${TOOL_NAME_PREFIX}get_overview"

/**
 * Where a session starts: what the user holds, what they owe, and the identifiers to say
 * so with.
 *
 * It exists to be the first call, so that nothing downstream ever has to guess an
 * identifier. Every figure here comes from the use case or repository that already owns
 * it — net worth from the ledger's own per-currency balance, an invoice's total from
 * `CalculateInvoiceUseCase`, a card's remaining limit from
 * `CalculateAvailableLimitUseCase` — and none of them is recomputed here.
 */
class GetOverviewTool(
    private val baseCurrency: IBaseCurrencyRepository,
    private val accounts: IAccountRepository,
    private val creditCards: ICreditCardRepository,
    private val invoices: IInvoiceRepository,
    private val entries: IEntryRepository,
    private val calculateBalance: CalculateBalanceUseCase,
    private val calculateInvoice: CalculateInvoiceUseCase,
    private val calculateAvailableLimit: CalculateAvailableLimitUseCase,
    private val exchangeRates: IExchangeRateRepository,
    private val money: MoneyPayloadFactory,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : McpTool {

    override val name: String = GET_OVERVIEW_TOOL

    override val title: String = "Get overview"

    override val description: String = """
        The state of the user's finances right now, and the entry point for identifiers:
        base currency, net worth, every account with its balance, every card with what it
        owes and what is left of its limit, and how far the exchange-rate archive reaches.

        Call this first. Every identifier the write tools take appears here or in
        $LIST_ACCOUNTS_TOOL, $LIST_CATEGORIES_TOOL and $LIST_INVOICES_TOOL — and a name is
        never a key on this surface.

        **`netWorth` and every other figure that can span accounts is a collection of
        amounts, one per currency, and stays a collection with a single currency in use.**
        Only a balance scoped to one account is a single amount, because an account
        declares one currency and cannot change it. The consolidated value in the base
        currency, when the archive can produce it, comes beside the collection and carries
        the rates that produced it; a missing rate is a warning and an absent
        consolidation, never a number.

        Debt reads as the magnitude owed. Spending reads negative, income positive.
        The reference date assumed comes back in `assumed`.
    """.trimIndent()

    override val inputSchema: JsonObject = objectSchema {
        stringProperty("referenceDate", "The date every figure is read at. Defaults to today, and the date used is echoed.")
    }

    override val outputSchema: JsonObject = toolOutcomeSchema(
        resultSchema = objectSchema(required = listOf("baseCurrency", "netWorth", "accounts", "creditCards", "assumed")) {
            stringProperty("baseCurrency", "ISO 4217. A display preference: no stored figure is denominated by it.")
            objectProperty("netWorth", moneyByCurrencySchema)
            objectProperty("assets", moneyByCurrencySchema)
            objectProperty("liabilities", moneyByCurrencySchema)
            arrayProperty("accounts", accountSchema, "Every account the user holds. Ledger system accounts are never among them.")
            arrayProperty(
                name = "creditCards",
                items = creditCardSchema,
                description = "Every card, with what it owes and what is left of its limit.",
            )
            objectProperty(
                name = "exchangeRateCoverage",
                schema = objectSchema(required = listOf("currenciesInUse", "pricedCurrencies")) {
                    arrayProperty(
                        name = "currenciesInUse",
                        items = buildJsonObject { put("type", "string") },
                        description = "Every currency the user's accounts and cards declare.",
                    )
                    arrayProperty(
                        name = "pricedCurrencies",
                        items = buildJsonObject { put("type", "string") },
                        description = "Those the archive can price at the reference date.",
                    )
                    arrayProperty(
                        name = "unpricedCurrencies",
                        items = buildJsonObject { put("type", "string") },
                        description = "Those it cannot. A figure spanning one of these has no consolidated value.",
                    )
                },
            )
            objectProperty("assumed", assumedSchema)
        },
        errorCodes = CommonToolCodes.all + AssumedDefaults.CODE_NOT_A_CIVIL_DATE,
    )

    override val annotations: ToolAnnotations = ToolAnnotations(readOnlyHint = true)

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        val args = Arguments(arguments)
        val referenceDate = args.date("referenceDate")
        args.failure?.let { return ToolOutcome.Failed(it) }

        val assumed = AssumedDefaults.resolve(
            today = clock.today(timeZone),
            timeZone = timeZone,
            referenceDate = referenceDate,
            archived = ArchivedScope.EXCLUDED,
        )
        val on = assumed.referenceDate.value
        val month = on.yearMonth

        val warnings = mutableListOf<ToolWarning>()

        // `Σ ASSET` and `Σ LIABILITY` come from the ledger's own per-currency reads, and
        // their sum is the ledger's own `plus` — liabilities are stored in credit, so
        // there is no sign rule of this surface's own anywhere here.
        val assetBalance = calculateBalance(month)
        val liabilityBalance = entries.naturalBalanceUpToByCurrency(month, AccountType.LIABILITY)

        val assets = money.spanning(assetBalance, DisplaySign.ofMoneyHeld, on)
        val liabilities = money.spanning(liabilityBalance, DisplaySign.of(AccountType.LIABILITY), on)
        val netWorth = money.spanning(assetBalance + liabilityBalance, DisplaySign.ofMoneyHeld, on)
        listOf(assets, liabilities, netWorth).forEach { it.collect(warnings) }

        val userAccounts = accounts.getAllAccounts()
        val cards = creditCards.getAllCreditCards()
        val openInvoices = invoices.getOpenInvoices().associateBy { it.creditCard.id }

        val rates = exchangeRates.ratesAsOf(on)
        val base = baseCurrency.observe().value
        val currenciesInUse = (userAccounts.map { it.currency } + cards.mapNotNull { it.currency })
            .distinct()
            .sorted()

        val accountPayloads = userAccounts.map { account ->
            buildJsonObject { putAccount(account, calculateBalance.forAccount(account.id, on)) }
        }

        val cardPayloads = cards.map { card ->
            val owed = openInvoices[card.id]?.let { calculateInvoice(it) } ?: 0.0
            val limit = calculateAvailableLimit(card)
            buildJsonObject {
                put("id", card.id)
                put("name", card.name)
                put("closingDay", card.closingDay)
                put("dueDay", card.dueDay)
                put("isArchived", card.isArchived)
                card.currency?.let { currency ->
                    put("currency", currency)
                    // Every figure of a card is denominated by its own `LIABILITY`
                    // account, so these are scalars — the one shape a read scoped to a
                    // single account may have. The owed figures arrive already read as the
                    // magnitude of the debt (`CalculateInvoiceUseCase`), so nothing here
                    // applies a sign a second time.
                    put("openInvoiceOwed", ToolJson.encodeToJsonElement(MoneyAmount.of(owed, currency)))
                    put("limit", ToolJson.encodeToJsonElement(MoneyAmount.of(card.limit, currency)))
                    put("unpaidTotal", ToolJson.encodeToJsonElement(MoneyAmount.of(limit.totalUnpaidAmount, currency)))
                    put("availableLimit", ToolJson.encodeToJsonElement(MoneyAmount.of(limit.available, currency)))
                }
                openInvoices[card.id]?.let { invoice ->
                    putJsonObject("openInvoice") {
                        put("id", invoice.id)
                        put("dueMonth", invoice.dueMonth.toString())
                        put("dueDate", invoice.dueDate.toString())
                    }
                }
            }
        }

        return ok(warnings = warnings.distinct()) {
            put("baseCurrency", base)
            put("netWorth", ToolJson.encodeToJsonElement(netWorth))
            put("assets", ToolJson.encodeToJsonElement(assets))
            put("liabilities", ToolJson.encodeToJsonElement(liabilities))

            putJsonArray("accounts") { accountPayloads.forEach { add(it) } }
            putJsonArray("creditCards") { cardPayloads.forEach { add(it) } }

            putJsonObject("exchangeRateCoverage") {
                putJsonArray("currenciesInUse") { currenciesInUse.forEach { add(it) } }
                putJsonArray("pricedCurrencies") {
                    currenciesInUse.filter { it == base || rates.containsKey(it) }.forEach { add(it) }
                }
                putJsonArray("unpricedCurrencies") {
                    currenciesInUse.filter { it != base && !rates.containsKey(it) }.forEach { add(it) }
                }
            }

            putAssumed(assumed)
        }
    }
}

internal val creditCardSchema: JsonObject = objectSchema(required = listOf("id", "name")) {
    integerProperty("id", "The opaque identifier of the card.")
    stringProperty("name", "What the user calls this card.")
    stringProperty("currency", "ISO 4217 of the card's own account. Every figure of the card is in it.")
    integerProperty("closingDay", "The day of the month a cycle closes on.")
    integerProperty("dueDay", "The day of the month a bill falls due on.")
    booleanProperty("isArchived", "A closed card: it keeps its history and accepts nothing new.")
    objectProperty("openInvoiceOwed", moneyAmountSchema)
    objectProperty("limit", moneyAmountSchema)
    objectProperty("unpaidTotal", moneyAmountSchema)
    objectProperty("availableLimit", moneyAmountSchema)
    objectProperty(
        name = "openInvoice",
        schema = objectSchema(required = listOf("id", "dueMonth")) {
            integerProperty("id", "The bill currently taking new purchases.")
            stringProperty("dueMonth", "YYYY-MM.")
            stringProperty("dueDate", "The day it falls due.")
        },
    )
}
