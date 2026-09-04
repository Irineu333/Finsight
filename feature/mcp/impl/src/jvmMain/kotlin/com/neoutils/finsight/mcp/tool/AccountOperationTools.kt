package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.SetDefaultAccountUseCase
import com.neoutils.finsight.domain.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finsight.domain.usecase.UpdateTransferUseCase
import com.neoutils.finsight.extension.destinationLeg
import com.neoutils.finsight.extension.sourceLeg
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentAccountWriteAnswer
import com.neoutils.finsight.mcp.surface.AgentFigure
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.surface.AgentTransactionWriteAnswer
import com.neoutils.finsight.mcp.surface.toAgentTransaction
import com.neoutils.finsight.ui.model.TransactionPerspective
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * The four operations on the user's own accounts: correcting a balance, moving money between two of
 * them, correcting a movement already made, and electing the account the app offers first.
 *
 * Three of the four post to the ledger, and none posts what the caller states. A correction of a
 * balance posts the **difference** between what the account holds and what it should hold, and a
 * transfer posts two legs the write boundary balances — on the way in and, when it is corrected, on
 * the way in again. What arrives here is the intent; the arithmetic has an owner.
 */

// ----------------------------------------------------------------------------------
// adjust_balance
// ----------------------------------------------------------------------------------

/**
 * **Corrects an account's balance to a stated figure, by posting the difference.**
 *
 * The balance is `Σ entries` and there is no number to overwrite, so the correction is itself a
 * posting — visible in the statement, and removable like any other. Re-adjusting the same date
 * rewrites that same posting from its own ledger leg rather than adding to it, which is why running
 * this twice with the same target leaves the balance at the target and not past it.
 */
internal class AdjustBalanceTool(
    private val clock: Clock,
    private val accountRepository: IAccountRepository,
    private val calculateBalance: CalculateBalanceUseCase,
    private val adjustBalance: AdjustBalanceUseCase,
) : McpTool {

    override val name: String = McpToolName.ADJUST_BALANCE.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Correct an account's balance to a stated figure, by posting the difference as an " +
            "adjustment. " +
            "PERIMETER: it posts an entry; it does not edit a number. A balance is the sum of an " +
            "account's entries, so the correction appears in the statement and can be removed " +
            "like any other posting. Adjusting to the balance the account already has is " +
            "refused: there is nothing to record. Recording what the money was actually spent on " +
            "is create_transaction, and it is the better answer whenever the user knows — an " +
            "adjustment says only that the figure was wrong."

    override val inputSchema = schema(
        "account_id" to number("The account to correct, from list_accounts."),
        "target_balance" to amount(
            "What the balance should be after the correction, in the account's own currency.",
        ),
        "date" to text(
            "The day the correction belongs to, as `2026-03-14`. Defaults to today, never in " +
                "the future: a balance is the sum of the entries up to a date, so correcting one " +
                "ahead of today corrects a reading nobody can take yet.",
        ),
        required = listOf("account_id", "target_balance"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val account = accountRepository.require(arguments.requiredLong("account_id"))
        val target = arguments.requiredMoney("target_balance")
        val date = arguments.date("date") ?: clock.today()

        adjustBalance(
            targetBalance = target,
            adjustmentDate = date,
            accountId = account.id,
        ).reported(
            summary = "balance of ${account.name} to $target",
            payload = {
                AgentAccountWriteAnswer(
                    // Read back rather than echoed: what the account holds now is the ledger's
                    // answer, and stating the target as though it were the outcome would be
                    // reporting the argument that was typed.
                    account = account.asAgentAccount().copy(
                        balance = AgentFigure.exact(
                            calculateBalance.forAccount(account.id, date),
                            account.currency,
                        ),
                    ),
                    note = "Corrected. The difference was posted as an adjustment dated $date; " +
                        "`balance` is the account as of that day.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.ACCOUNT, account.id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// transfer
// ----------------------------------------------------------------------------------

/**
 * **Moves money between two of the user's own accounts.**
 *
 * **No rate is a parameter anywhere on this path.** When the two accounts differ in currency the
 * caller states both ends — what left and what arrived, which is what the statement shows — and the
 * rate is the *quotient* of the two, derived by the domain afterwards and written to the rate
 * archive. Asking for a rate here would be asking for a number the operation already implies, and
 * would let it disagree with the money that actually moved.
 *
 * The transaction arrives at the write boundary incomplete when the currencies differ, and the
 * boundary completes it: each currency's residue lands on that currency's conversion account. So
 * nothing in this tool, and nothing in the use case, has to know how such a transaction balances.
 */
internal class TransferTool(
    private val clock: Clock,
    private val accountRepository: IAccountRepository,
    private val transferBetweenAccounts: TransferBetweenAccountsUseCase,
) : McpTool {

    override val name: String = McpToolName.TRANSFER.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Move money between two of the user's own accounts. " +
            "PERIMETER: both ends belong to the user, so nothing here is income or spending and " +
            "no month's totals move. Paying a card bill is pay_invoice, and money leaving for " +
            "somebody else is create_transaction. " +
            "When the two accounts are denominated differently, give destination_amount — what " +
            "arrived, in the destination's currency. There is no rate to give: it is the " +
            "quotient of the two ends, and the app derives and records it from them."

    override val inputSchema = schema(
        "from_account_id" to number("The account the money leaves, from list_accounts."),
        "to_account_id" to number("The account the money arrives in, from list_accounts."),
        "amount" to amount("What leaves the source, in the source account's own currency."),
        "destination_amount" to amount(
            "What arrives, in the destination account's own currency — only when the two " +
                "currencies differ. Leave it out otherwise: the same figure arrives.",
        ),
        "date" to text("The day it happened, as `2026-03-14`. Defaults to today, never in the future."),
        "title" to text(
            "Why the money moved, as the user said it. It names the posting and classifies " +
                "nothing — a transfer has no category. Left out, the posting has no name.",
        ),
        required = listOf("from_account_id", "to_account_id", "amount"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val source = accountRepository.require(arguments.requiredLong("from_account_id"))
        val destination = accountRepository.require(arguments.requiredLong("to_account_id"))
        val amount = arguments.requiredMoney("amount")
        val destinationAmount = arguments.money("destination_amount")
        val date = arguments.date("date") ?: clock.today()

        transferBetweenAccounts(
            sourceAccountId = source.id,
            destinationAccountId = destination.id,
            amount = amount,
            date = date,
            destinationAmount = destinationAmount,
            // A blank title is a transfer with nothing stated, which is what absence already means
            // here: there is no title to take back from a posting being created. Reading it that
            // way is `string`'s doing, and it is the one place that decision is made.
            title = arguments.string("title"),
        ).reported(
            summary = "$amount from ${source.name} to ${destination.name}",
            payload = { transaction ->
                // Read from the source, which is the end the caller stated: the direction is the
                // money leaving it, in its own currency.
                val posting = transaction.toAgentTransaction(
                    perspective = TransactionPerspective(source.id),
                )
                AgentTransactionWriteAnswer(
                    transaction = posting,
                    note = noteFor(
                        posting = posting,
                        done = if (destinationAmount == null) {
                            "Moved. Neither end is spending or income, so no month's totals changed."
                        } else {
                            "Moved across currencies: $amount left ${source.name} and " +
                                "$destinationAmount arrived in ${destination.name}. The rate " +
                                "between them was derived from those two figures and recorded " +
                                "for $date."
                        },
                    ),
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.TRANSACTION, it.id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// update_transfer
// ----------------------------------------------------------------------------------

/**
 * **Corrects a transfer that is already registered, in place.**
 *
 * The counterpart of [TransferTool], and the same rules judge it: a correction is a transfer being
 * restated, not a lesser act. What differs is the write — the legs are rewritten and the operation
 * keeps its identity, which is what separates this from removing one and registering another.
 *
 * **Every field is carried rather than patched.** What the call does not name is taken from the
 * operation as the ledger holds it now, so correcting only the date cannot move the money by
 * omission. The one field that is not simply carried is `destination_amount`: it is what the other
 * end received, and it exists only while the two ends are denominated differently — so it is
 * carried under exactly that condition, and a correction that brings both ends into one currency
 * drops it rather than turning a single figure into two.
 */
internal class UpdateTransferTool(
    private val transactionRepository: ITransactionRepository,
    private val accountRepository: IAccountRepository,
    private val updateTransfer: UpdateTransferUseCase,
) : McpTool {

    override val name: String = McpToolName.UPDATE_TRANSFER.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Correct a transfer that was already recorded — its two accounts, its amount, its date " +
            "or its title. What is not given keeps the value it already has, and the operation " +
            "keeps its identity: the legs are rewritten, so this is not the same as removing it " +
            "and recording another. " +
            "An empty title erases the one it has, leaving the posting named by its form alone. " +
            "PERIMETER: only a transfer is corrected here — both ends are the user's own " +
            "accounts, so the correction is still neither income nor spending and no month's " +
            "totals move. An expense or an income is update_transaction and a card payment is " +
            "${McpToolName.UPDATE_ADVANCE_INVOICE_PAYMENT.wireName}. " +
            "When the two accounts are denominated differently, destination_amount is what " +
            "arrives; there is no rate to give, and the app derives and records it from the two " +
            "ends this correction states. Correcting a crossing while naming only one of the two " +
            "ends leaves the other as it was, and the rate between them changes accordingly."

    override val inputSchema = schema(
        "id" to number("The transfer to correct, from list_transactions."),
        "from_account_id" to number("Move the outgoing end to this account, from list_accounts."),
        "to_account_id" to number("Move the incoming end to this account, from list_accounts."),
        "amount" to amount("The new amount leaving the source, in the source account's own currency."),
        "destination_amount" to amount(
            "The new amount arriving, in the destination account's own currency — only when the " +
                "two currencies differ.",
        ),
        "date" to text("The new day, as `2026-03-14`. Never in the future."),
        "title" to text(
            "The new title. Keeps the one the transfer has when not given; pass an empty string " +
                "to erase it.",
        ),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")

        val stored = transactionRepository.getTransactionById(id)
            ?: return@writing refusedWith(
                AgentRefusal.notFound("transaction", id),
                summary = "correct transfer $id",
            )

        val summary = "transfer ${stored.amount}, ${stored.date}"

        // What the operation *is* decides which form corrects it, and the answer has one owner.
        if (stored.label != TransactionLabel.TRANSFER) {
            return@writing refusedWith(stored.correctedElsewhere(name), summary)
        }

        val storedSource = stored.entries.sourceLeg()
        val storedDestination = stored.entries.destinationLeg()

        if (storedSource == null || storedDestination == null) {
            return@writing refusedWith(
                AgentRefusal(
                    reason = "This transfer does not state both of its ends, so there is nothing " +
                        "to correct it from.",
                ),
                summary = summary,
            )
        }

        val source = arguments.long("from_account_id")?.let { accountRepository.require(it) }
            ?: storedSource.account
        val destination = arguments.long("to_account_id")?.let { accountRepository.require(it) }
            ?: storedDestination.account

        val amount = arguments.money("amount") ?: storedSource.amount.absoluteAmount()

        // Absent means "as it arrived", and only while there are two figures to state. Where the
        // correction leaves both ends in one currency there is no second figure at all, and
        // carrying the old one would state a crossing the operation no longer is.
        val arriving = arguments.money("destination_amount")
            ?: storedDestination.amount.absoluteAmount()
                .takeIf { source.currency != destination.currency }

        updateTransfer(
            transactionId = id,
            sourceAccountId = source.id,
            destinationAccountId = destination.id,
            amount = amount,
            date = arguments.date("date") ?: stored.date,
            title = arguments.stringOr("title", stored.title),
            destinationAmount = arriving,
        ).reported(
            summary = "$amount from ${source.name} to ${destination.name}",
            payload = {
                // Read back rather than echoed: the corrected operation is the ledger's answer,
                // and the crossing it may have become is only visible there.
                val posting = transactionRepository.getTransactionById(id)?.toAgentTransaction(
                    perspective = TransactionPerspective(source.id),
                )
                AgentTransactionWriteAnswer(
                    transaction = posting,
                    note = noteFor(
                        posting = posting,
                        done = "Corrected, and it is still the same operation. Everything the " +
                            "call did not name kept the value it had." +
                            if (arriving == null) {
                                ""
                            } else {
                                " $amount leaves ${source.name} and $arriving arrives in " +
                                    "${destination.name}; the rate between them was derived from " +
                                    "those two figures and recorded."
                            },
                    ),
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.TRANSACTION, id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// set_default_account
// ----------------------------------------------------------------------------------

/**
 * **Elects the account the app offers first, and demotes whichever held the role.**
 *
 * The role is exclusive and always filled, so this never clears it: an identity matching no open
 * account is refused rather than leaving the app with no default at all.
 */
internal class SetDefaultAccountTool(
    private val accountRepository: IAccountRepository,
    private val setDefaultAccount: SetDefaultAccountUseCase,
) : McpTool {

    override val name: String = McpToolName.SET_DEFAULT_ACCOUNT.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Make an account the one the app offers first when something new is recorded. " +
            "PERIMETER: it changes a preference and moves no money. Exactly one account holds " +
            "the role at a time, so whichever held it is demoted by the same call; there is no " +
            "way to leave the app without a default, and an archived account cannot take the role."

    override val inputSchema = schema(
        "id" to number("The account to elect, from list_accounts."),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")
        val stored = accountRepository.require(id)

        setDefaultAccount(id).reported(
            summary = "default account is now ${stored.name}",
            payload = {
                AgentAccountWriteAnswer(
                    account = (accountRepository.getAccountById(id) ?: stored).asAgentAccount(),
                    note = "Elected. Whichever account held the role no longer does.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.ACCOUNT, id) },
        )
    }
}
