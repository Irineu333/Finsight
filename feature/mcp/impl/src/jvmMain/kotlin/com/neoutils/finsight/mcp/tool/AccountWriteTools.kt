package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.usecase.CreateAccountUseCase
import com.neoutils.finsight.domain.usecase.DeleteAccountUseCase
import com.neoutils.finsight.domain.usecase.UpdateAccountUseCase
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentAccount
import com.neoutils.finsight.mcp.surface.AgentAccountWriteAnswer
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.surface.AgentRemovalAnswer
import com.neoutils.finsight.util.AppIcon
import kotlinx.serialization.json.JsonObject

/** An account as an agent receives it back from a write — no figure, because none was read. */
internal fun Account.asAgentAccount() = AgentAccount(
    id = id,
    name = name,
    currency = currency,
    isDefault = isDefault,
    isArchived = isArchived,
    yieldsInterest = yieldsInterest,
)

// ----------------------------------------------------------------------------------
// create_account
// ----------------------------------------------------------------------------------

/**
 * **Creates one of the user's accounts.**
 *
 * The currency is required and has no default here for the same reason it has none in the use
 * case: the account form is the one door a second currency is born through, and an account is
 * denominated once and never again. A tool that filled it in from the base currency would create,
 * silently, an account in a currency nobody chose.
 */
internal class CreateAccountTool(
    private val accountRepository: IAccountRepository,
    private val createAccount: CreateAccountUseCase,
) : McpTool {

    override val name: String = McpToolName.CREATE_ACCOUNT.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Create one of the user's accounts. " +
            "The currency is required and is fixed from this moment on: it is what every figure " +
            "of the account is denominated in, and no later edit changes it. " +
            "PERIMETER: it creates the account and nothing in it. An opening balance is not a " +
            "property of an account — post it with adjust_balance or create_transaction. " +
            "The account is born with the app's default icon; icons are not part of this surface."

    override val inputSchema = schema(
        "name" to text("What the user calls it. Must not clash with an account that already exists."),
        "currency" to text("The ISO code it is denominated in, as `BRL` or `USD`. Fixed from now on."),
        "is_default" to yesOrNo("Make it the account new postings are offered on. Defaults to false."),
        "yields_interest" to yesOrNo(
            "Whether the account's own money is remunerated, which is what makes a yield " +
                "posting legal on it. Defaults to false.",
        ),
        required = listOf("name", "currency"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val name = arguments.requiredString("name")
        val currency = arguments.requiredString("currency").uppercase()
        val isDefault = arguments.flag("is_default", default = false)
        val yieldsInterest = arguments.flag("yields_interest", default = false)

        createAccount(
            name = name,
            isDefault = isDefault,
            iconKey = AppIcon.WALLET.key,
            currency = currency,
            yieldsInterest = yieldsInterest,
        ).map {
            // Read back rather than echoed: being made the default is a second write the use
            // case performs after the insert, so the value it answers with predates it.
            accountRepository.getAccountById(it.id) ?: it
        }.reported(
            summary = "account $name in $currency",
            payload = {
                AgentAccountWriteAnswer(
                    account = it.asAgentAccount(),
                    note = "Created. It holds nothing yet: a balance comes from postings.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.ACCOUNT, it.id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// update_account
// ----------------------------------------------------------------------------------

/**
 * **Edits an account — everything about it except its denomination.**
 *
 * The patch becomes the `(Account) -> Account` the use case applies to the account **as it is when
 * the operation runs**, not to a copy the agent once listed. Naming the currency is refused there,
 * unconditionally, and the refusal is the domain's own words.
 */
internal class UpdateAccountTool(
    private val accountRepository: IAccountRepository,
    private val updateAccount: UpdateAccountUseCase,
) : McpTool {

    override val name: String = McpToolName.UPDATE_ACCOUNT.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Change an account's name, whether it is the default one, or whether it yields. " +
            "What is not given keeps the value it already has. " +
            "PERIMETER: an account's currency is part of its identity and cannot be changed — " +
            "creating another account is the only way to hold money in another currency. " +
            "Archiving and unarchiving are archive_entity and unarchive_entity, not this."

    override val inputSchema = schema(
        "id" to number("The account to edit, from list_accounts."),
        "name" to text("The new name."),
        "is_default" to yesOrNo("Make it the account new postings are offered on."),
        "yields_interest" to yesOrNo("Whether the account's own money is remunerated."),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")
        val stored = accountRepository.require(id)

        val name = arguments.string("name")
        val isDefault = arguments.flagOrNull("is_default")
        val yields = arguments.flagOrNull("yields_interest")

        updateAccount(id) { account ->
            account.copy(
                name = name ?: account.name,
                isDefault = isDefault ?: account.isDefault,
                yieldsInterest = yields ?: account.yieldsInterest,
            )
        }.reported(
            summary = "account ${stored.name}",
            payload = {
                AgentAccountWriteAnswer(
                    account = it.asAgentAccount(),
                    note = "Edited. Everything the call did not name kept the value it had.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.ACCOUNT, it.id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// delete_account
// ----------------------------------------------------------------------------------

/** **Removes an account that never moved.** */
internal class DeleteAccountTool(
    private val accountRepository: IAccountRepository,
    private val deleteAccount: DeleteAccountUseCase,
) : McpTool {

    override val name: String = McpToolName.DELETE_ACCOUNT.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Remove an account for good. " +
            "PERIMETER: only an account that never moved can go. One with postings, one a " +
            "recurring template still points at, and the default one are refused — the entries " +
            "that name it stay valid and its history is not the app's to discard. The refusal " +
            "names archive_entity, which keeps the account and its history and takes it out of " +
            "every selector."

    override val inputSchema = schema(
        "id" to number("The account to remove, from list_accounts."),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")

        val stored = accountRepository.getAccountById(id)
            ?: return@writing refusedWith(
                AgentRefusal.notFound("account", id),
                summary = "delete account $id",
            )

        deleteAccount(id).reported(
            summary = "account ${stored.name}",
            payload = {
                AgentRemovalAnswer(
                    removed = "account",
                    id = id,
                    name = stored.name,
                    note = "Removed. It had no movement, so nothing in the ledger went with it.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.ACCOUNT, id) },
        )
    }
}
