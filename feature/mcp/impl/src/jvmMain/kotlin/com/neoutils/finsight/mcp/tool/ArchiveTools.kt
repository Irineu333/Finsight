package com.neoutils.finsight.mcp.tool

import arrow.core.Either
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.ArchiveAccountUseCase
import com.neoutils.finsight.domain.usecase.ArchiveCategoryUseCase
import com.neoutils.finsight.domain.usecase.ArchiveCreditCardUseCase
import com.neoutils.finsight.domain.usecase.ArchiveRecurringUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveAccountUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveCategoryUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveCreditCardUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveRecurringUseCase
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentArchiveAnswer
import com.neoutils.finsight.mcp.surface.AgentRefusal
import kotlinx.serialization.json.JsonObject

/**
 * The one pair of **generic** tools on this surface: retiring something from circulation, and
 * bringing it back.
 *
 * They are generic because the operation genuinely is one operation seen four times — an account, a
 * card, a category and a template all leave the selectors and keep their history — and four
 * near-identical tools would cost the agent four descriptions to read and give it four chances to
 * pick the wrong one. What is *not* generic is the rule: each kind has its own owner, and each
 * refusal below is that owner's.
 *
 * **The prose and the parameter say the same four words, from the same list.** `mcp-tool-surface`
 * requires exactly that of a discriminated tool, and the requirement is not pedantry: a description
 * naming a fifth kind that the parameter rejects is invisible until a call fails, and it teaches the
 * consumer to distrust the only material it has for choosing. So [ARCHIVABLE] is spelled once and
 * spliced into both, and adding a kind to one without the other is impossible rather than merely
 * discouraged.
 */

/** What can be retired and brought back — the discriminator's domain, and the description's list. */
private val ARCHIVABLE = listOf("account", "card", "category", "recurring")

/** The kind of thing a reference names, per discriminator, so the log resolves what it points at. */
private val REFERENCE_KINDS = mapOf(
    "account" to AgentActivity.Reference.Kind.ACCOUNT,
    "card" to AgentActivity.Reference.Kind.CREDIT_CARD,
    "category" to AgentActivity.Reference.Kind.CATEGORY,
    "recurring" to AgentActivity.Reference.Kind.RECURRING,
)

/**
 * What the four facades have in common as far as these two tools are concerned: a name to put in the
 * log, and a flag to read back afterwards.
 *
 * Resolved **before** the operation for the name and **after** it for the flag. The name has to be
 * read first because the log records what the thing was called at that instant; the flag has to be
 * read last because the whole effect of the operation is that flag, and echoing what the caller
 * asked for would make the answer true whatever happened.
 */
private class Retirable(
    val name: String,
    val isArchived: suspend () -> Boolean,
)

/**
 * The four lookups, in one place, so the two tools resolve identically.
 *
 * Every flag is read **through the repository**, not off the object fetched a moment earlier: a
 * card's closure in particular lives on the ledger account it projects onto, and a by-id read is
 * what carries it back.
 */
private class RetirableLookup(
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val categoryRepository: ICategoryRepository,
    private val recurringRepository: IRecurringRepository,
) {
    suspend fun of(type: String, id: Long): Retirable = when (type) {
        "account" -> accountRepository.require(id).let { account ->
            Retirable(account.name) { accountRepository.getAccountById(id)?.isArchived == true }
        }

        "card" -> creditCardRepository.require(id).let { card ->
            Retirable(card.name) {
                creditCardRepository.getCreditCardById(id)?.isArchived == true
            }
        }

        "category" -> categoryRepository.require(id).let { category ->
            Retirable(category.name) { categoryRepository.getCategoryById(id)?.isArchived == true }
        }

        "recurring" -> recurringRepository.getRecurringById(id).let { recurring ->
            recurring ?: throw BadArgument(AgentRefusal.notFound("recurring", id))
            Retirable(recurring.label) {
                recurringRepository.getRecurringById(id)?.isArchived == true
            }
        }

        // Unreachable while the schema enumerates ARCHIVABLE and nothing else; written out so a
        // kind added to the list without a lookup fails loudly instead of silently doing nothing.
        else -> throw BadArgument(
            AgentRefusal(reason = "`type` must be one of ${ARCHIVABLE.joinToString(", ")}."),
        )
    }
}

// ----------------------------------------------------------------------------------
// archive_entity
// ----------------------------------------------------------------------------------

/**
 * **Retires something from circulation while keeping everything it was part of.**
 *
 * It is the operation a refusal to delete points at, which is why the two live on different
 * permission axes: removing discards history, and this preserves it.
 */
internal class ArchiveEntityTool(
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val categoryRepository: ICategoryRepository,
    private val recurringRepository: IRecurringRepository,
    private val archiveAccount: ArchiveAccountUseCase,
    private val archiveCreditCard: ArchiveCreditCardUseCase,
    private val archiveCategory: ArchiveCategoryUseCase,
    private val archiveRecurring: ArchiveRecurringUseCase,
) : McpTool {

    override val name: String = McpToolName.ARCHIVE_ENTITY.wireName

    override val effect = McpToolEffect.CHANGES

    private val lookup = RetirableLookup(
        accountRepository = accountRepository,
        creditCardRepository = creditCardRepository,
        categoryRepository = categoryRepository,
        recurringRepository = recurringRepository,
    )

    override val description: String =
        "Take one of ${ARCHIVABLE.joinToString(", ")} out of circulation, keeping everything it " +
            "was part of. It disappears from the pickers and the active lists; its postings stay " +
            "where they are and go on naming it. " +
            "PERIMETER: it applies to exactly those four kinds and to nothing else. " +
            "It is what a refused removal points at — delete_* discards history, and this keeps " +
            "it. An account or a card holding a balance is refused: archiving invents no " +
            "write-off, so the money is moved, spent or corrected first. An archived recurring " +
            "stops generating cycles; the ones it already generated are untouched. " +
            "Undo it with unarchive_entity."

    override val inputSchema = schema(
        "type" to choice("Which kind the identifier belongs to.", ARCHIVABLE),
        "id" to number("What to retire, from the listing of that kind."),
        required = listOf("type", "id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val type = arguments.requiredOneOf("type", ARCHIVABLE)
        val id = arguments.requiredLong("id")
        val subject = lookup.of(type, id)

        val outcome: Either<Throwable, Unit> = when (type) {
            "account" -> archiveAccount(id)
            "card" -> archiveCreditCard(id)
            "category" -> archiveCategory(id)
            else -> archiveRecurring(id)
        }

        outcome.reported(
            summary = "$type ${subject.name} archived",
            payload = {
                AgentArchiveAnswer(
                    entity = type,
                    id = id,
                    name = subject.name,
                    isArchived = subject.isArchived(),
                    note = "Retired. It is out of the selectors and the active lists, and " +
                        "everything that referenced it still does.",
                )
            },
            reference = { reference(REFERENCE_KINDS.getValue(type), id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// unarchive_entity
// ----------------------------------------------------------------------------------

/**
 * **Brings something archived back into circulation** — the exact inverse of [ArchiveEntityTool],
 * over the same four kinds.
 *
 * Reversible and innocuous everywhere, with one thing it deliberately does not do: a recurring comes
 * back generating from the current cycle, and the months that elapsed while it was archived are not
 * generated retroactively.
 */
internal class UnarchiveEntityTool(
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val categoryRepository: ICategoryRepository,
    private val recurringRepository: IRecurringRepository,
    private val unarchiveAccount: UnarchiveAccountUseCase,
    private val unarchiveCreditCard: UnarchiveCreditCardUseCase,
    private val unarchiveCategory: UnarchiveCategoryUseCase,
    private val unarchiveRecurring: UnarchiveRecurringUseCase,
) : McpTool {

    override val name: String = McpToolName.UNARCHIVE_ENTITY.wireName

    override val effect = McpToolEffect.CHANGES

    private val lookup = RetirableLookup(
        accountRepository = accountRepository,
        creditCardRepository = creditCardRepository,
        categoryRepository = categoryRepository,
        recurringRepository = recurringRepository,
    )

    override val description: String =
        "Bring one of ${ARCHIVABLE.joinToString(", ")} back into circulation, undoing " +
            "archive_entity. It reappears in the pickers and the active lists. " +
            "PERIMETER: it applies to exactly those four kinds and to nothing else. " +
            "It restores availability and nothing more: an account comes back as an ordinary " +
            "one, never as the default, and a recurring resumes from the current cycle — the " +
            "months that passed while it was archived are not generated retroactively."

    override val inputSchema = schema(
        "type" to choice("Which kind the identifier belongs to.", ARCHIVABLE),
        "id" to number("What to bring back, from the listing of that kind."),
        required = listOf("type", "id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val type = arguments.requiredOneOf("type", ARCHIVABLE)
        val id = arguments.requiredLong("id")
        val subject = lookup.of(type, id)

        val outcome: Either<Throwable, Unit> = when (type) {
            "account" -> unarchiveAccount(id)
            "card" -> unarchiveCreditCard(id)
            "category" -> unarchiveCategory(id)
            else -> unarchiveRecurring(id)
        }

        outcome.reported(
            summary = "$type ${subject.name} unarchived",
            payload = {
                AgentArchiveAnswer(
                    entity = type,
                    id = id,
                    name = subject.name,
                    isArchived = subject.isArchived(),
                    note = "Back in circulation. Nothing that happened while it was archived was " +
                        "recreated.",
                )
            },
            reference = { reference(REFERENCE_KINDS.getValue(type), id) },
        )
    }
}
