package com.neoutils.finsight.mcp.prompt

import com.neoutils.finsight.mcp.contract.TOOL_NAME_PREFIX
import com.neoutils.finsight.mcp.tool.AGGREGATE_TRANSACTIONS_TOOL
import com.neoutils.finsight.mcp.tool.GET_OVERVIEW_TOOL
import com.neoutils.finsight.mcp.tool.LIST_ACCOUNTS_TOOL
import com.neoutils.finsight.mcp.tool.LIST_BUDGETS_TOOL
import com.neoutils.finsight.mcp.tool.LIST_CATEGORIES_TOOL
import com.neoutils.finsight.mcp.tool.LIST_INVOICES_TOOL
import com.neoutils.finsight.mcp.tool.LIST_RECURRING_TOOL
import com.neoutils.finsight.mcp.tool.LIST_TRANSACTIONS_TOOL

/** One argument a prompt takes. */
data class PromptArgument(
    val name: String,
    val description: String,
    val required: Boolean = false,
)

/**
 * One flow the user invokes by name.
 *
 * **A prompt is text and not logic.** It cannot decide which rule applies, and that is
 * exactly why the user's own vocabulary — "record this month's statement", "review the
 * month" — is offered here instead of as an aggregating tool: a tool named after the
 * user's phrasing would have to decide, on its own, things the domain already decides
 * (which bill a purchase falls in, what a transaction is), and it would decide them a
 * second time.
 *
 * So these name the tools that already exist and introduce none.
 */
class McpPrompt(
    val name: String,
    val title: String,
    val description: String,
    val arguments: List<PromptArgument>,
    private val body: (Map<String, String>) -> String,
) {

    /** The text this prompt expands to, with whatever arguments the client filled in. */
    fun render(arguments: Map<String, String> = emptyMap()): String = body(arguments)
}

/** Everything this server offers as a prompt. */
class PromptRegistry(prompts: List<McpPrompt>) {

    val prompts: List<McpPrompt> = prompts.sortedBy { it.name }

    private val byName = prompts.associateBy { it.name }

    init {
        require(byName.size == prompts.size) {
            "Two prompts announce the same name: " +
                prompts.map { it.name }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        }
        prompts.forEach { prompt ->
            require(prompt.name.startsWith(TOOL_NAME_PREFIX)) {
                "`${prompt.name}` is not prefixed by `$TOOL_NAME_PREFIX`"
            }
            require(prompt.description.isNotBlank()) { "`${prompt.name}` has no description" }
        }
    }

    fun find(name: String): McpPrompt? = byName[name]
}

/** The name of the prompt that walks a statement into the ledger. */
const val RECORD_STATEMENT_PROMPT: String = "${TOOL_NAME_PREFIX}record_statement"

/** The name of the prompt that reviews a month. */
const val REVIEW_MONTH_PROMPT: String = "${TOOL_NAME_PREFIX}review_month"

/**
 * The two flows the user invokes by name.
 *
 * Both are guidance over the tools that exist. Neither introduces a verb of its own, and
 * neither states a rule of the domain: where a rule would be needed — which bill a purchase
 * falls in, whether a transaction is spending — the text points at the tool whose answer
 * carries it.
 */
fun userFlowPrompts(): PromptRegistry = PromptRegistry(
    listOf(
        McpPrompt(
            name = RECORD_STATEMENT_PROMPT,
            title = "Record a statement",
            description = "Walk a bank or card statement into the ledger: read the identifiers first, " +
                "preview what would be written, then record it in one call.",
            arguments = listOf(
                PromptArgument("statement", "The statement's lines, as text or a table.", required = false),
                PromptArgument("accountOrCard", "Which account or card the statement belongs to.", required = false),
            ),
        ) { arguments ->
            val source = arguments["accountOrCard"]?.takeIf { it.isNotBlank() }
            buildString {
                appendLine("You are recording a statement into Finsight.")
                appendLine()
                appendLine("1. Call `$GET_OVERVIEW_TOOL` first, and `$LIST_ACCOUNTS_TOOL` and")
                appendLine("   `$LIST_CATEGORIES_TOOL` if you need more. Every write takes")
                appendLine("   **identifiers** from these reads — never a name, and never a name you inferred.")
                appendLine("2. Classify only in categories that already exist. If a line fits none, leave it")
                appendLine("   unclassified: nothing here creates a category, and a category invented per")
                appendLine("   statement is expensive to undo.")
                appendLine("3. Preview the whole batch before writing it. The preview says which invoice each")
                appendLine("   card purchase would fall in — do not work that out yourself.")
                appendLine("4. Write the batch in a **single** call with an idempotency key. If you retry,")
                appendLine("   reuse the same key **with the same items**; a new set of items needs a new key.")
                appendLine("5. Read the per-item outcome. An item flagged as a probable duplicate was still")
                appendLine("   recorded — tell the user, and let them decide.")
                appendLine()
                appendLine("Dates are civil dates, YYYY-MM-DD. Amounts carry their currency and an integer")
                appendLine("number of cents. Do not send a rate for anything: state both ends instead.")
                source?.let {
                    appendLine()
                    appendLine("The statement belongs to: $it. Confirm its identifier from the reads above.")
                }
                arguments["statement"]?.takeIf { it.isNotBlank() }?.let {
                    appendLine()
                    appendLine("The statement:")
                    appendLine(it)
                }
            }
        },
        McpPrompt(
            name = REVIEW_MONTH_PROMPT,
            title = "Review the month",
            description = "Go over a month: what was spent and earned, how the budgets stand, " +
                "which bills are due and which recurring templates are waiting.",
            arguments = listOf(
                PromptArgument("startDate", "First day of the month, YYYY-MM-DD.", required = true),
                PromptArgument("endDate", "Last day of the month, YYYY-MM-DD.", required = true),
            ),
        ) { arguments ->
            val start = arguments["startDate"]?.takeIf { it.isNotBlank() } ?: "<startDate>"
            val end = arguments["endDate"]?.takeIf { it.isNotBlank() } ?: "<endDate>"
            buildString {
                appendLine("You are reviewing $start..$end in Finsight.")
                appendLine()
                appendLine("1. `$GET_OVERVIEW_TOOL` for where things stand and for the identifiers.")
                appendLine("2. `$AGGREGATE_TRANSACTIONS_TOOL` over $start..$end for every total —")
                appendLine("   by category, then by month or by account. **Never add up the pages of**")
                appendLine("   **`$LIST_TRANSACTIONS_TOOL`**: adding outside the server ignores the currency")
                appendLine("   of each account and counts as spending what the domain does not.")
                appendLine("3. `$LIST_TRANSACTIONS_TOOL` only for the individual lines worth naming — the")
                appendLine("   largest ones, or those left unclassified.")
                appendLine("4. `$LIST_BUDGETS_TOOL`, `$LIST_INVOICES_TOOL` and `$LIST_RECURRING_TOOL` for the")
                appendLine("   budgets, the bills and the templates still waiting to be confirmed.")
                appendLine()
                appendLine("Every figure that can span accounts is a **collection of amounts, one per**")
                appendLine("**currency** — even with a single currency in use. When the consolidated value")
                appendLine("is absent, say so; do not add the amounts together yourself, and never assume a")
                appendLine("rate of one. Spending reads negative and income positive.")
                appendLine()
                appendLine("This flow reads. Do not write anything unless the user asks for it.")
            }
        },
    ),
)
