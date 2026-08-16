package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **A public use case is identified by the id of what it operates on, and that form is
 * the one that carries the rule.**
 *
 * The four properties below are shape, not behaviour: they are true of *what is
 * declared*, so nothing that runs can observe them and only the sources can be read.
 * They matter because the app is about to grow a second door — a surface that receives a
 * request from outside holds an identifier and nothing else, while a screen holds the
 * aggregate it loaded to display. A use case reachable only by handing it a loaded
 * aggregate forces that second caller to repeat the resolution, and the refusal for an
 * identity that matches nothing then has as many wordings as there are callers.
 *
 * Each check keeps its own list of the use cases that legitimately fall outside it, by
 * name and with the reason. The lists are compared **both ways**: a use case that stops
 * satisfying the rule fails because it is not listed, and one that starts satisfying it
 * fails because it still is. Adding a name is therefore a decision somebody makes in a
 * review, which is the whole point — a predicate that excluded a category ("every
 * `Calculate*`", "everything that does not answer `Either`") would silence exactly the
 * case the rule exists to catch.
 */
class UseCaseIdentityTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    // ------------------------------------------------------------------------------
    // The declarations, read off the sources
    // ------------------------------------------------------------------------------

    /** A parameter exactly as the signature declares it. */
    private data class Parameter(val name: String, val type: String, val default: String?)

    /** One `invoke` overload. [body] is `null` when the interface leaves it abstract. */
    private data class Overload(
        val parameters: List<Parameter>,
        val returnType: String,
        val body: String?,
    )

    private data class UseCase(
        val name: String,
        val path: String,
        val overloads: List<Overload>,
    ) {
        val abstractForms: List<Overload> get() = overloads.filter { it.body == null }
        val delegatingForms: List<Overload> get() = overloads.filter { it.body != null }
    }

    /** Every `.kt` of a feature `api`, whatever the target it is compiled for. */
    private val apiSources: List<File> = File(repoRoot, "feature").walkTopDown()
        .onEnter { it.name != "build" }
        .filter { it.isFile && it.extension == "kt" }
        .filter { Regex("""^feature/[^/]+/api/src/[a-zA-Z]*Main/""") in it.relativePath() }
        .toList()

    private val useCases: List<UseCase> = apiSources.flatMap { file ->
        val text = file.readText().withoutComments()
        Regex("""\binterface\s+(\w+UseCase)\s*\{""").findAll(text).map { declaration ->
            val opening = declaration.range.last
            UseCase(
                name = declaration.groupValues[1],
                path = file.relativePath(),
                overloads = overloadsOf(text.substring(opening, text.matching(opening) + 1)),
            )
        }
    }

    /**
     * What counts as a domain aggregate: a model of `:core:model` or `:core:ledger` that
     * carries an identity. It is read off the sources rather than listed here, so a model
     * written tomorrow is covered the day it is written.
     */
    private val aggregates: Set<String> = sequenceOf("core/model", "core/ledger")
        .map { File(repoRoot, "$it/src/commonMain/kotlin/com/neoutils/finsight/domain/model") }
        .flatMap { it.walkTopDown() }
        .filter { it.isFile && it.extension == "kt" }
        .flatMap { file ->
            val text = file.readText().withoutComments()
            Regex("""\bclass\s+(\w+)\s*\(""").findAll(text).mapNotNull { declaration ->
                val opening = declaration.range.last
                declaration.groupValues[1].takeIf {
                    "val id: Long" in text.substring(opening, text.matching(opening) + 1)
                }
            }
        }
        .toSet()

    // ------------------------------------------------------------------------------
    // The rules
    // ------------------------------------------------------------------------------

    /**
     * The scan is what every assertion below rests on, so a glob that stopped matching
     * would turn all of them green at once. This is the floor under that.
     */
    @Test
    fun `the scan reaches the api surface it claims to read`() {
        assertTrue(
            useCases.size >= 40,
            "only ${useCases.size} use case interfaces were found in the feature `api`s; " +
                "the scan is no longer reading the surface it asserts about.",
        )
        assertTrue(
            "Account" in aggregates && "Invoice" in aggregates && "Recurring" in aggregates,
            "the domain aggregates were not recognised: $aggregates",
        )
    }

    @Test
    fun `the form that carries the implementation is identified by id`() {
        val withoutIdentity = useCases
            .filterNot { useCase ->
                useCase.abstractForms.any { form -> form.parameters.any { it.namesAnIdentity } }
            }
            .map { it.name }
            .toSortedSet()

        assertEquals(
            NOTHING_TO_IDENTIFY.keys.toSortedSet(),
            withoutIdentity,
            "A public use case has to be reachable by the identity of what it operates " +
                "on, and the by-id form is the one that carries the rule.\n" +
                (withoutIdentity - NOTHING_TO_IDENTIFY.keys).joinToString("\n") {
                    "  NO FORM BY ID: $it — a caller holding only an identifier cannot reach it"
                } +
                (NOTHING_TO_IDENTIFY.keys - withoutIdentity).joinToString("\n") {
                    "  LISTED BUT COMPLIANT: $it — it takes an identity now; drop the entry"
                },
        )
    }

    /**
     * The other half: offering the aggregate form is a convenience of calling, never a
     * second implementation. Two forms with two bodies are two rules one edit away from
     * disagreeing, and the disagreement would show up as the screen and the agent doing
     * different things to the same account.
     */
    @Test
    fun `every other form delegates, and never carries the implementation`() {
        val defects = mutableListOf<String>()

        for (useCase in useCases) {
            if (useCase.abstractForms.size != 1) {
                defects += "${useCase.name} declares ${useCase.abstractForms.size} " +
                    "implementation-carrying `invoke`; a use case has one rule (${useCase.path})"
            }

            for (form in useCase.delegatingForms) {
                val body = form.body.orEmpty()
                if (!body.startsWith("invoke(")) {
                    defects += "${useCase.name} has a form whose body is not a delegation: " +
                        "`$body` (${useCase.path})"
                }
                defects += useCase.identityLeaks(form, body)
            }

            for (form in useCase.abstractForms) {
                defects += useCase.identityLeaks(form, body = null)
            }
        }

        assertEquals(
            emptyList(),
            defects.toList(),
            "The aggregate form exists so a caller that already holds the aggregate can " +
                "say so. It extracts the identity and delegates; anything else is a " +
                "second implementation of one rule.\n" + defects.joinToString("\n") { "  $it" },
        )
    }

    /**
     * A form that resolves an identity can be handed one that matches nothing, and the
     * refusal is part of the operation rather than of each caller. The exceptions are
     * reads, and a read has nothing to refuse: absence is a number, not a failure.
     */
    @Test
    fun `a use case that resolves an identity refuses a typed not-found`() {
        val silent = useCases
            .filter { useCase ->
                useCase.abstractForms.any { form -> form.parameters.any { it.namesAnIdentity } }
            }
            .filterNot { useCase -> useCase.abstractForms.all { it.returnType.startsWith("Either<") } }
            .map { it.name }
            .toSortedSet()

        assertEquals(
            ABSENCE_IS_THE_NEUTRAL_VALUE.keys.toSortedSet(),
            silent,
            "A use case that resolves an identity refuses the one that matches nothing, " +
                "with a typed error, before doing anything.\n" +
                (silent - ABSENCE_IS_THE_NEUTRAL_VALUE.keys).joinToString("\n") {
                    "  NO TYPED REFUSAL: $it — an identity matching nothing has nowhere to be reported"
                } +
                (ABSENCE_IS_THE_NEUTRAL_VALUE.keys - silent).joinToString("\n") {
                    "  LISTED BUT COMPLIANT: $it — it refuses now; drop the entry"
                },
        )
    }

    /**
     * A default read off another parameter is only expressible while that parameter is an
     * aggregate, which ties the shape of the operation to the shape of calling it. It also
     * announces a behaviour a caller may legitimately not want: the six such defaults
     * `ConfirmRecurringUseCase` used to declare were each avoided on purpose by its only
     * caller, one of them under a comment explaining that falling back to the template
     * "would hand the user a name they had just erased".
     *
     * When absence means something, the meaning is resolved in the body and documented,
     * so it is the same meaning for every caller — including one with no form to prefill.
     */
    @Test
    fun `no parameter default is read off an aggregate the caller also passes`() {
        val derived = useCases.flatMap { useCase ->
            useCase.overloads.flatMap { form ->
                val aggregatesInScope = form.parameters.filter { it.aggregateType != null }
                form.parameters
                    .filter { it.default != null }
                    .flatMap { parameter ->
                        aggregatesInScope
                            .filter { it.name != parameter.name }
                            .filter { Regex("""\b${it.name}\b""") in parameter.default!! }
                            .map {
                                "${useCase.name}: `${parameter.name}` defaults to " +
                                    "`${parameter.default}`, read off the aggregate " +
                                    "`${it.name}` (${useCase.path})"
                            }
                    }
            }
        }

        assertEquals(
            emptyList(),
            derived,
            "A parameter default derived from an aggregate the same call receives makes " +
                "the operation impossible to state without the aggregate, and hands every " +
                "caller a behaviour only some of them want.\n" +
                derived.joinToString("\n") { "  $it" },
        )
    }

    // ------------------------------------------------------------------------------
    // The declared exceptions
    // ------------------------------------------------------------------------------

    private companion object {

        /**
         * Use cases with no identity to resolve, each with the reason it has none. Every
         * entry is a claim about the operation itself, not about the shape of its
         * signature, and it is meant to be argued with in a review.
         */
        val NOTHING_TO_IDENTIFY = mapOf(
            // Creation. There is nothing to resolve: the thing the operation acts on is
            // the one it brings into existence, and it answers it, identity included.
            "CreateAccountUseCase" to "creates the account it operates on",
            "CreateCategoryUseCase" to "creates the category it operates on",
            "CreateBudgetUseCase" to "creates the budget it operates on",
            "AddCreditCardUseCase" to "creates the card it operates on",
            "AddInstallmentUseCase" to "creates the plan and the transactions of it",

            // A form is a description of something that does not exist yet, so there is
            // no identity anywhere on these paths — including the dispatch that decides
            // which of the three a filled form turns out to describe.
            "ValidateTransactionFormUseCase" to "judges a form, which names nothing stored",
            "BuildTransactionUseCase" to "turns a form into the intent the ledger can write",
            "RegisterTransactionUseCase" to "registers a filled form, whatever it describes",

            // Reads whose subject is a period or a perspective and not one item: they
            // answer for every category, or for the accounts a perspective resolves to,
            // in one query. An id parameter would be answering a different question.
            "CalculateCategorySpendingUseCase" to "answers for every expense category of a month",
            "CalculateCategoryIncomeUseCase" to "answers for every income category of a month",
            "CalculateReportStatsUseCase" to "answers for a perspective over a period",

            // The only exception here that is a judgement rather than a fact about the shape,
            // so it is worth stating plainly. Its answer *is* keyed by the invoice's id — what
            // it reads by is the dimension, which the invoice carries and the id does not. Every
            // caller already holds the invoices for something else, so taking identities would
            // buy a read nobody needs, on a use case that today touches only the ledger. If a
            // caller ever arrives holding ids alone, this entry is the one to delete rather than
            // to reword.
            "CalculateInvoiceUseCase" to "every caller already holds the invoices it asks about",

            // It suggests an icon for an account that does not exist yet, so it takes
            // nothing at all — the answer is about the icons already in use.
            "SuggestAccountIconUseCase" to "suggests for a new account, and takes no argument",
        )

        /**
         * Reads where an identity matching nothing is not an error to refuse but a figure
         * to answer — the neutral one. The spec says so itself: an identity with no result
         * is *absent from the map*, and the caller reads it as the neutral value.
         */
        val ABSENCE_IS_THE_NEUTRAL_VALUE = mapOf(
            // A card that does not resolve is absent from the map and reads as `Limit.NONE`
            // — the same answer a card with no unpaid invoice and no limit gets. Nothing is
            // written, so there is nothing to withhold by refusing.
            "CalculateAvailableLimitUseCase" to "an unresolved card reads as `Limit.NONE`",

            // Its two guards are questions about an **identity** — whether anything still
            // points at this template — so it never loads the recurring and has no
            // resolution to fail. Deliberately unlike `ResolveCategoryRetirabilityUseCase`,
            // which *does* load the category (it reads `dimensionId` and `systemKey`) and
            // therefore does refuse `NOT_FOUND`. Both say why in their KDoc, and the
            // asymmetry is the correct one: refusing an identity that matches no recurring
            // belongs to the operation that removes one.
            "ResolveRecurringRetirabilityUseCase" to "nothing is loaded, so nothing can fail to resolve",
        )
    }

    // ------------------------------------------------------------------------------
    // Reading Kotlin well enough to ask these questions of it
    // ------------------------------------------------------------------------------

    /** An identity is a `Long` named `id`/`<x>Id`, or a collection of them named `<x>Ids`. */
    private val Parameter.namesAnIdentity: Boolean
        get() = when {
            type == "Long" -> name == "id" || name.endsWith("Id")
            Regex("""^(List|Set|Collection)<Long>$""").matches(type) -> name.endsWith("Ids")
            else -> false
        }

    /** The aggregate this parameter carries, alone or in a collection, if it carries one. */
    private val Parameter.aggregateType: String?
        get() {
            val bare = type.trim().removeSuffix("?")
            val element = Regex("""^(?:List|Set|Collection)<(.+)>$""")
                .find(bare)?.groupValues?.get(1) ?: bare
            return element.trim().removeSuffix("?").takeIf { it in aggregates }
        }

    /**
     * An aggregate the same interface also names by identity, carried by a form that does
     * not reduce it to that identity — the exact shape of "the aggregate form carries the
     * rule". [body] is `null` for the form that carries the implementation, and that form
     * may hold no such aggregate at all: the identity is what it is written against.
     */
    private fun UseCase.identityLeaks(form: Overload, body: String?): List<String> =
        form.parameters
            .filter { it.aggregateType != null }
            .filter { parameter ->
                overloads.any { other ->
                    other.parameters.any {
                        it.name == parameter.name + "Id" || it.name == parameter.name + "Ids"
                    }
                }
            }
            .filter { body == null || "${it.name}.id" !in body }
            .map {
                "$name takes `${it.name}: ${it.type}` on the form that carries the rule, " +
                    "while naming the same thing by identity elsewhere ($path)"
            }

    private fun File.relativePath() = relativeTo(repoRoot).invariantSeparatorsPath

    /**
     * Comments replaced by the newlines they held, so what is left is code at the lines it
     * was written on. Reading them as code would find `invoke(` in the prose that explains
     * one.
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

    /** The index of the delimiter closing the one at [opening]. */
    private fun String.matching(opening: Int): Int {
        val open = this[opening]
        val close = if (open == '(') ')' else '}'
        var depth = 0
        for (i in opening until length) {
            if (this[i] == open) depth++
            if (this[i] == close && --depth == 0) return i
        }
        error("unbalanced `$open` at $opening")
    }

    /** The index of [target] outside every bracket — `->` is a token, not a bracket. */
    private fun String.indexOutsideBrackets(target: Char): Int {
        var depth = 0
        var i = 0
        while (i < length) {
            if (startsWith("->", i)) {
                i += 2
                continue
            }
            when (this[i]) {
                '(', '[', '<' -> depth++
                ')', ']', '>' -> depth--
                target -> if (depth == 0) return i
            }
            i++
        }
        return -1
    }

    private fun String.splitOutsideBrackets(): List<String> {
        val parts = mutableListOf<String>()
        var rest = this
        while (true) {
            val comma = rest.indexOutsideBrackets(',')
            if (comma < 0) break
            parts += rest.substring(0, comma)
            rest = rest.substring(comma + 1)
        }
        parts += rest
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun parameterOf(declaration: String): Parameter {
        val colon = declaration.indexOutsideBrackets(':')
        val name = declaration.substring(0, colon).trim()
        val rest = declaration.substring(colon + 1)
        val assignment = rest.indexOutsideBrackets('=')
        return if (assignment < 0) {
            Parameter(name, rest.trim(), default = null)
        } else {
            Parameter(name, rest.substring(0, assignment).trim(), rest.substring(assignment + 1).trim())
        }
    }

    private fun overloadsOf(body: String): List<Overload> =
        Regex("""(?:suspend\s+)?operator\s+fun\s+invoke\s*\(""").findAll(body).map { declaration ->
            val opening = declaration.range.last
            val closing = body.matching(opening)
            val tail = body.substring(closing + 1)
            val signature = tail.substringBefore('\n')
            val assignment = signature.indexOutsideBrackets('=')
            Overload(
                parameters = body.substring(opening + 1, closing)
                    .splitOutsideBrackets()
                    .map(::parameterOf),
                returnType = signature
                    .let { if (assignment < 0) it else it.substring(0, assignment) }
                    .trim()
                    .removePrefix(":")
                    .trim(),
                body = if (assignment < 0) null else {
                    tail.substring(assignment + 1)
                        .substringBefore("\n}")
                        .substringBefore("\n\n")
                        .trim()
                },
            )
        }.toList()
}
