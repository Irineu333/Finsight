@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.mcp.contract.ArchivedScope
import com.neoutils.finsight.mcp.contract.AssumedDefaults
import com.neoutils.finsight.mcp.contract.Cursor
import com.neoutils.finsight.mcp.contract.DisplaySign
import com.neoutils.finsight.mcp.contract.McpTool
import com.neoutils.finsight.mcp.contract.MoneyAmount
import com.neoutils.finsight.mcp.contract.PageLimit
import com.neoutils.finsight.mcp.contract.ResponseLimits
import com.neoutils.finsight.mcp.contract.TOOL_NAME_PREFIX
import com.neoutils.finsight.mcp.contract.ToolAnnotations
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.resolvePageLimit
import com.neoutils.finsight.mcp.contract.toolOutcomeSchema
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The name of the tool that lists the user's accounts, fixed here because the overview
 * and the orientation resources name it as the way to reach an account identifier.
 */
const val LIST_ACCOUNTS_TOOL: String = "${TOOL_NAME_PREFIX}list_accounts"

/**
 * The user's accounts, each with the balance of the account itself.
 *
 * **System accounts appear nowhere here, and that is not a filter this tool applies.**
 * The two nominals, reconciliation and conversion are created on demand by the ledger's
 * write boundary; they are mechanism, not a fact about the user, and the account facade
 * (`IAccountRepository`) has never contained them — every one of its reads is scoped to
 * `ASSET`. An agent that could see them would name them to the user as somewhere their
 * money went.
 */
class ListAccountsTool(
    private val accounts: IAccountRepository,
    private val calculateBalance: CalculateBalanceUseCase,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : McpTool {

    override val name: String = LIST_ACCOUNTS_TOOL

    override val title: String = "List accounts"

    override val description: String = """
        The user's accounts — the identifiers every other tool takes for an account.

        Each account declares exactly one currency, fixed when it was created, so its
        balance is a single amount and not a per-currency collection. Figures that can
        span accounts — net worth, spending, invoice totals — are collections even when a
        single currency is in use; see $GET_OVERVIEW_TOOL.

        Credit cards are not accounts and are not listed here: they are in
        $GET_OVERVIEW_TOOL, with their invoices in $LIST_INVOICES_TOOL.

        Archived accounts are left out unless asked for, and the scope applied comes back
        in `assumed`.
    """.trimIndent()

    override val inputSchema: JsonObject = objectSchema {
        pagingProperties()
        archivedProperty()
        stringProperty("referenceDate", "The date balances are read at. Defaults to today, and the date used is echoed.")
    }

    override val outputSchema: JsonObject = toolOutcomeSchema(
        resultSchema = listingSchema(
            itemsName = "accounts",
            item = accountSchema,
            description = "The user's accounts. System accounts of the ledger are never among them.",
        ),
        errorCodes = CommonToolCodes.all +
            ResponseLimits.CODE_PAGE_LIMIT_ABOVE_CEILING +
            ResponseLimits.CODE_PAGE_LIMIT_NOT_POSITIVE +
            AssumedDefaults.CODE_NOT_A_CIVIL_DATE,
    )

    override val annotations: ToolAnnotations = ToolAnnotations(readOnlyHint = true)

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        val args = Arguments(arguments)
        val archived = args.enum("archived", ArchivedScope.entries.toTypedArray())
        val referenceDate = args.date("referenceDate")
        val requestedLimit = args.int("limit")
        val cursor = args.string("cursor")?.let(::Cursor)
        args.failure?.let { return ToolOutcome.Failed(it) }

        val limit = when (val resolved = resolvePageLimit(requestedLimit)) {
            is PageLimit.Refused -> return ToolOutcome.Failed(resolved.error)
            is PageLimit.Accepted -> resolved.limit
        }

        val assumed = AssumedDefaults.resolve(
            today = clock.today(timeZone),
            timeZone = timeZone,
            referenceDate = referenceDate,
            archived = archived,
        )

        val visible = accounts.inScope(assumed.archived.value)
        val page = paginate(visible, limit, cursor) { it.id.toString() }

        val items = page.items.map { account ->
            buildJsonObject {
                putAccount(
                    account = account,
                    balance = calculateBalance.forAccount(account.id, assumed.referenceDate.value),
                )
            }
        }

        return ok {
            putPage("accounts", page.with(items))
            putAssumed(assumed)
        }
    }
}

/**
 * The accounts a scope admits, read from the facade that owns the distinction.
 *
 * The repository has two reads and not a flag — the active facade and the one that
 * includes closed accounts — so `ONLY` is the difference between them, taken here rather
 * than by a third query nobody else would use.
 */
internal suspend fun IAccountRepository.inScope(scope: ArchivedScope): List<Account> = when (scope) {
    ArchivedScope.EXCLUDED -> getAllAccounts()
    ArchivedScope.INCLUDED -> getAllAccountsIncludingClosed()
    ArchivedScope.ONLY -> getAllAccountsIncludingClosed().filter { it.isArchived }
}

/**
 * One account, with the balance of the account itself.
 *
 * The balance is **scalar** and this is the one shape on the surface that may be: an
 * account declares one currency and cannot change it, so there is nothing here a
 * collection would be protecting against.
 */
internal fun JsonObjectBuilder.putAccount(account: Account, balance: Double) {
    put("id", account.id)
    put("name", account.name)
    put("currency", account.currency)
    put("iconKey", account.iconKey)
    put("isDefault", account.isDefault)
    put("isArchived", account.isArchived)
    put("yieldsInterest", account.yieldsInterest)
    put(
        "balance",
        ToolJson.encodeToJsonElement(
            MoneyAmount.of(value = DisplaySign.ofMoneyHeld * balance, currency = account.currency),
        ),
    )
}

/** The same account, reduced to what a nested reference needs. */
internal fun JsonObjectBuilder.putAccountRef(name: String, account: Account) =
    putRef(name, account.id, account.name)

internal val accountSchema: JsonObject = objectSchema(
    required = listOf("id", "name", "currency", "balance"),
) {
    integerProperty("id", "The opaque identifier. A name is never a key on this surface.")
    stringProperty("name", "What the user calls this account.")
    stringProperty("currency", "ISO 4217. Declared at creation and immutable.")
    stringProperty("iconKey", "The icon the app renders this account with.")
    booleanProperty("isDefault", "Whether new transactions land here unless another is chosen.")
    booleanProperty("isArchived", "A closed account: it keeps its history and accepts nothing new.")
    booleanProperty("yieldsInterest", "Whether the account's own money is remunerated.")
    objectProperty(
        name = "balance",
        schema = moneyAmountSchema,
    )
}
