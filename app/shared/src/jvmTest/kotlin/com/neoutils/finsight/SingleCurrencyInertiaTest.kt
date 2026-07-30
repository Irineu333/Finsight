package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **Exactly two places let a currency be chosen** — the account form and the card form.
 *
 * This is the inertia test of the multi-currency change, **inverted rather than
 * deleted**. While the app was single-currency it asserted that nothing could denominate
 * an account in anything but the one currency the device resolved, which made the risk
 * window between "the write boundary records the currency" and "every read aggregates by
 * currency" unreachable rather than merely narrow. The forms have since opened the
 * choice, so the same test now pins the door open at exactly two hinges — and it holds
 * forever, because a third place that decides the currency of an account is a place the
 * user cannot see and cannot correct (design D12: it never changes afterwards).
 *
 * It reads the repository's own sources, which no other test here does. A currency is
 * chosen at a *construction site*, and nothing observable at runtime can enumerate
 * sites — only the text can.
 */
class SingleCurrencyInertiaTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    /** Every production Kotlin source of the repository — no test source, no build output. */
    private val productionSources: List<File> = repoRoot.walkTopDown()
        .onEnter { it.name != "build" && it.name != ".git" }
        .filter { it.isFile && it.extension == "kt" }
        .filter { file ->
            val path = file.relativeTo(repoRoot).invariantSeparatorsPath
            "/src/" in path && Regex("/src/[a-zA-Z]*Main/") in path
        }
        .toList()

    private fun File.relativePath() = relativeTo(repoRoot).invariantSeparatorsPath

    /**
     * The one expression that *decides* a currency: the device's region, reduced to
     * something the catalog offers. Naming it here is what makes a second way of
     * deciding fail this test instead of passing quietly.
     */
    private val theOneResolver = "CurrencyCatalog.reduce(localeCurrencyCode())"

    private val expectedDeciders = setOf(
        // The account a brand-new install starts with no longer appears here: it
        // reads the seeded base currency instead of resolving one of its own, which
        // is the whole point of 9.1 — one resolution, not two that can disagree.
        // `CreateAccountUseCase` no longer appears either: its currency parameter lost
        // its default when the form began stating one, so it decides nothing.
        // The currency a **new card** is pre-selected with, before the form has an
        // account to read one from. The user is free to change it, and what is written
        // is whatever `insert` is given.
        "feature/creditcards/impl/src/commonMain/kotlin/com/neoutils/finsight/database/repository/CreditCardRepository.kt",
        // Fabricated accounts of a dashboard component preview, which has to look
        // like the app it previews.
        "feature/dashboard/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/dashboard/DashboardPreviewFactory.kt",
        // The base currency of consolidation — the *other* thing the locale resolves
        // (design D28). It denominates no account, so it does not widen the window
        // this test closes; it is here because it uses the same one resolver, and a
        // second way of deciding must fail whichever of the two it decides.
        "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/database/repository/BaseCurrencyRepository.kt",
    )

    @Test
    fun `every production site that decides a currency decides it the one way`() {
        val found = productionSources
            .filter { theOneResolver in it.readText() }
            .map { it.relativePath() }
            .toSet()

        assertEquals(
            expectedDeciders,
            found,
            diagnosis(
                new = found - expectedDeciders,
                gone = expectedDeciders - found,
                whatItMeans = "A production site decides the currency of an account. " +
                    "While the app is single-currency there is exactly one way to " +
                    "decide, and these are the only places allowed to use it.",
            ),
        )
    }

    /**
     * The other half, and the one that outlives the change: the currency of an account
     * is **chosen** in exactly two places, and both are forms the user is looking at.
     *
     * The budget form appears here too and is not a third hinge: it picks the
     * denomination of a *limit* among currencies that already exist, and creates none
     * (design D13). The distinction is the whole reason the account form shows its row
     * even with a single currency while the budget form shows nothing.
     */
    @Test
    fun `exactly two forms let the user choose an account's currency`() {
        val choosesACurrency = Regex("""CurrencySelected""")

        val expected = setOf(
            // The shared sheet itself: it renders whatever list it is handed and
            // decides nothing about accounts.
            "core/designsystem/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/currencyPicker/CurrencyPickerModal.kt",
            // The account form: the door a second currency is born through.
            "feature/accounts/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/accountForm/AccountFormAction.kt",
            "feature/accounts/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/accountForm/AccountFormModal.kt",
            "feature/accounts/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/accountForm/AccountFormViewModel.kt",
            // The card form: the same door, for the card's `LIABILITY` account.
            "feature/creditcards/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/creditCardForm/CreditCardFormAction.kt",
            "feature/creditcards/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/creditCardForm/CreditCardFormModal.kt",
            "feature/creditcards/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/creditCardForm/CreditCardFormViewModel.kt",
            // Not a third door: the denomination of a budget limit, chosen among the
            // currencies the user already holds, creating none.
            "feature/budgets/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/budgetForm/BudgetFormAction.kt",
            "feature/budgets/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/budgetForm/BudgetFormModal.kt",
            "feature/budgets/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/budgetForm/BudgetFormViewModel.kt",
            // The rate form picks which currency a rate is *about* — an observation, not
            // an account.
            "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/exchangeRateForm/ExchangeRateFormModal.kt",
        )

        val found = productionSources
            .filter { choosesACurrency.containsMatchIn(it.readText()) }
            .map { it.relativePath() }
            .toSet()

        assertEquals(
            expected,
            found,
            diagnosis(
                new = found - expected,
                gone = expected - found,
                whatItMeans = "A form lets a currency be chosen. Choosing the currency " +
                    "of an account is allowed in exactly two of them, because a wrong " +
                    "choice can never be corrected afterwards (D12) — a third one is a " +
                    "place the user cannot see and cannot undo.",
            ),
        )
    }

    @Test
    fun `no production site names a currency in a literal`() {
        val literal = Regex("""currency\s*=\s*"[A-Z]{3}"""")
        val found = productionSources
            .filter { literal.containsMatchIn(it.readText()) }
            .map { it.relativePath() }
            .toSet()

        assertEquals(
            emptySet(),
            found,
            diagnosis(
                new = found,
                gone = emptySet(),
                whatItMeans = "A hard-coded currency code is how the app used to be " +
                    "accidentally correct: it read `BASE_CURRENCY` and meant `BRL`. " +
                    "A currency is resolved, propagated from a row, or chosen by the " +
                    "user — never written down.",
            ),
        )
    }

    private fun diagnosis(new: Set<String>, gone: Set<String>, whatItMeans: String) = buildString {
        appendLine(whatItMeans)
        new.forEach { appendLine("  NEW: $it") }
        gone.forEach { appendLine("  GONE: $it — the expected set is out of date") }
    }
}
