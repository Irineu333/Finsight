package com.neoutils.finsight.mcp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **No tool writes to a repository. Every write goes through the use case that owns it.**
 *
 * This is the structural half of `mcp-tool-surface`'s first requirement, and the reason it is
 * structural is that the failure it guards against passes every behavioural test. A tool that
 * inserted a category straight through `ICategoryRepository` would create categories perfectly: the
 * row would be there, the payload would be right, and nothing would fail — except that the name
 * would not be trimmed, the uniqueness check would not have run, and the app would have two answers
 * to "what a category is", one for the screen and one for the agent. The two disagree on the first
 * edit either of them receives, and the disagreement is invisible until a user notices two
 * categories called the same thing.
 *
 * It is the sibling of `ViewModelWritesGoThroughUseCasesTest`, which holds the same line on the
 * other surface, and it is written the same way: it reads the sources, because this is a fact about
 * what is **declared** — nothing that runs can observe it, and a tool written tomorrow is covered
 * the day it is written, without anybody adding it to a list.
 */
class RegistrationToolsGoThroughUseCasesTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val toolPackage = File(
        repoRoot,
        "feature/mcp/impl/src/jvmMain/kotlin/com/neoutils/finsight/mcp/tool",
    )

    /** Every source of the tool package: the tools themselves and the plumbing they share. */
    private val sources: List<File> = toolPackage.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    /**
     * A write is one of these verbs applied to a repository or a DAO. The receiver is what makes it
     * precise: `update` on a form and `insert` on a builder are not writes, and no verb alone tells
     * them apart from `updateTransaction` on a repository.
     *
     * The verbs are the ones the repository and DAO interfaces actually declare — the same list the
     * view-model guard uses, so the two surfaces are held to one definition of "a write".
     */
    private val write = Regex(
        """\b(\w*(?:[Rr]epository|[Dd]ao))\s*\.\s*""" +
            """((?:insert|upsert|update|delete|save|remove|create|add|set|put|write|clear""" +
            """|archive|unarchive|close|reopen|record|emit|detach|dismiss|confirm)\w*)\s*\(""",
    )

    private fun directWritesIn(text: String): List<String> = write.findAll(text.withoutComments())
        .map { "${it.groupValues[1]}.${it.groupValues[2]}" }
        .distinct()
        .sorted()
        .toList()

    private val direct: Map<String, List<String>> = sources
        .associate { file ->
            file.relativeTo(repoRoot).invariantSeparatorsPath to directWritesIn(file.readText())
        }
        .filterValues { it.isNotEmpty() }

    // ------------------------------------------------------------------------------
    // The floor: everything below rests on the scan reaching what it claims to read
    // ------------------------------------------------------------------------------

    @Test
    fun `the scan reaches the tools it claims to read`() {
        assertTrue(
            sources.size >= 20,
            "only ${sources.size} sources were found in $toolPackage; the scan is no longer " +
                "reading the surface it asserts about.",
        )
        assertTrue(
            declaredTools.size >= 40,
            "only ${declaredTools.size} tools were recognised; a tool declaration is no longer " +
                "matched, so the assertions below prove nothing.",
        )
    }

    /**
     * The scan has to *recognise* a direct write, or its silence means nothing.
     *
     * The sample is the shape the guard exists to catch, in the exact spelling a tool would use it
     * in — a repository named the way the tools name theirs, with a write verb the interfaces
     * declare. Without this, a regex that stopped matching would turn the test below green forever.
     */
    @Test
    fun `the scan recognises a direct write when it sees one`() {
        val forged = """
            internal class ForgedTool(
                private val categoryRepository: ICategoryRepository,
            ) : McpTool {
                override suspend fun call(arguments: JsonObject?) = writing {
                    categoryRepository.insert(Category(name = "whatever"))
                }
            }
        """.trimIndent()

        assertEquals(listOf("categoryRepository.insert"), directWritesIn(forged))
    }

    // ------------------------------------------------------------------------------
    // The rules
    // ------------------------------------------------------------------------------

    /**
     * The comparison is exact: there is no allow-list, because there is no tool that legitimately
     * writes to a repository. Every operation this surface offers already has an owner — that is
     * what groups 2, 3 and 4 were for — and the one that did not (editing a posting) grew one
     * rather than being written here a second time.
     */
    @Test
    fun `no tool writes to a repository the domain owns`() {
        assertEquals(
            emptyMap(),
            direct,
            "A write from a tool is a second copy of a rule the app already owns, and the copy " +
                "the screen does not use is the one that drifts.\n" +
                direct.entries.joinToString("\n") {
                    "  ${it.key} — ${it.value}; the operation belongs to a use case"
                },
        )
    }

    /**
     * The other half, and the one that catches a write reaching the store by some other route: a
     * tool that changes something **holds** the use case it changes it through.
     *
     * A tool with no use case in its constructor and `CHANGES` on it is writing through something
     * that is not an owner — a DAO, a database handle, a repository under another name — or it is
     * not changing anything and is mislabelled. Both are defects, and neither shows up in a payload.
     */
    @Test
    fun `every tool that changes something holds the use case it changes it through`() {
        val ownerless = declaredTools
            .filter { it.changes }
            .filterNot { tool -> tool.parameters.any { it.endsWith("UseCase") } }
            .map { "${it.name} (${it.path})" }

        assertEquals(
            emptyList(),
            ownerless,
            "A tool that changes something reaches the domain through the use case that owns the " +
                "operation, and holds it to do so.\n" + ownerless.joinToString("\n") { "  $it" },
        )
    }

    /**
     * **No tool holds the use case that writes an invoice's status without posting the payment.**
     *
     * `mcp-tool-surface` states it in as many words: a tool that alters state *derived from
     * postings* reaches it through the use case that posts, and marking an invoice paid without
     * writing the payment produces a balance incoherent with the ledger **without anything
     * failing**. `PayInvoiceUseCase` is exactly that half — it writes `status = PAID` and nothing
     * else — and it is one letter's difference from `PayInvoicePaymentUseCase`, which posts the
     * payment and then comes through it.
     *
     * Structural rather than behavioural on purpose. The behavioural test exists too, and it asserts
     * both consequences; this one catches the substitution at the point where it is made, and covers
     * every tool written afterwards without anybody remembering to add one.
     */
    @Test
    fun `no tool holds the use case that marks an invoice paid without posting the payment`() {
        val holders = declaredTools
            .filter { tool -> STATUS_ONLY_PAYMENT in tool.held }
            .map { "${it.name} (${it.path})" }

        assertEquals(
            emptyList(),
            holders,
            "`$STATUS_ONLY_PAYMENT` writes the status and no money. A tool settling a bill " +
                "through it would leave the account's balance lying, with nothing failing — " +
                "`PayInvoicePaymentUseCase` is the one that does both.\n" +
                holders.joinToString("\n") { "  $it" },
        )
        // The floor under it: the tool that pays does exist and does hold the other one, so the
        // emptiness above is a fact about which use case was chosen rather than about neither
        // being present.
        assertTrue(
            declaredTools.any { tool -> FULL_PAYMENT in tool.held },
            "no tool holds `$FULL_PAYMENT`, so the check above passes over a surface that " +
                "cannot pay a bill at all",
        )
    }

    /** And the converse floor: the ones that only read hold none, so the check above discriminates. */
    @Test
    fun `a tool that only reads holds no write use case, so the check above is not vacuous`() {
        val readers = declaredTools.filterNot { it.changes }

        assertTrue(readers.size >= 20, "the read families were not recognised: ${readers.size}")
        assertTrue(
            readers.none { tool ->
                tool.parameters.any { it.startsWith("Create") || it.startsWith("Delete") }
            },
            "a tool that only reads holds an operation that writes: " +
                readers.filter { tool ->
                    tool.parameters.any { it.startsWith("Create") || it.startsWith("Delete") }
                },
        )
    }

    // ------------------------------------------------------------------------------
    // Reading Kotlin well enough to ask these questions of it
    // ------------------------------------------------------------------------------

    private data class DeclaredTool(
        val name: String,
        val path: String,
        val parameters: List<String>,
        val changes: Boolean,
    ) {
        /**
         * What the tool holds, by simple name.
         *
         * A type written out in full is the same collaborator as one that was imported, and an
         * assertion that could be evaded by qualifying a name would be an assertion about import
         * style rather than about what the tool reaches.
         */
        val held: Set<String> = parameters
            .map { it.substringBefore('<').substringAfterLast('.').trim() }
            .toSet()
    }

    /** Every class in the package that declares [McpTool] as a supertype, with what it holds. */
    private val declaredTools: List<DeclaredTool> = sources.flatMap { file ->
        val text = file.readText().withoutComments()
        Regex("""\bclass\s+(\w+)\s*\(""").findAll(text).mapNotNull { declaration ->
            val opening = declaration.range.last
            val closing = text.matching(opening)
            val tail = text.substring(closing + 1).substringBefore('\n')
            if (!Regex("""^\s*:\s*McpTool\b""").containsMatchIn(tail)) return@mapNotNull null

            val body = text.substring(closing)
            DeclaredTool(
                name = declaration.groupValues[1],
                path = file.relativeTo(repoRoot).invariantSeparatorsPath,
                parameters = text.substring(opening + 1, closing)
                    .split(',')
                    .mapNotNull { it.substringAfter(':', "").trim().takeIf(String::isNotEmpty) },
                // Read off the declaration rather than by instantiating one: the question is what
                // the source says this tool is, which is the same thing the registry announces.
                changes = "McpToolEffect.CHANGES" in body.substringBefore("\n}"),
            )
        }
    }

    /**
     * Comments replaced by the newlines they held, so what is left is code. Reading them as code
     * would find `categoryRepository.insert(` in the prose that explains why a tool does not.
     */
    private fun String.withoutComments(): String {
        val code = StringBuilder(length)
        var i = 0
        while (i < length) {
            when {
                startsWith("/*", i) -> {
                    val end = indexOf("*/", i).let { if (it < 0) length else it + 2 }
                    substring(i, end).forEach { if (it == '\n') code.append('\n') }
                    i = end
                }

                startsWith("//", i) -> i = indexOf('\n', i).let { if (it < 0) length else it }
                else -> code.append(this[i++])
            }
        }
        return code.toString()
    }

    private companion object {

        /** Writes `status = PAID` and moves no money. Nothing on this surface may settle through it. */
        const val STATUS_ONLY_PAYMENT = "PayInvoiceUseCase"

        /** Posts the payment **and** marks the invoice paid — the one a bill is settled through. */
        const val FULL_PAYMENT = "PayInvoicePaymentUseCase"
    }

    /** The index of the parenthesis closing the one at [opening]. */
    private fun String.matching(opening: Int): Int {
        var depth = 0
        for (i in opening until length) {
            if (this[i] == '(') depth++
            if (this[i] == ')' && --depth == 0) return i
        }
        error("unbalanced `(` at $opening")
    }
}
