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
     * The one expression that *decides* a currency: what the device's locale names,
     * kept only when the app's base-100 arithmetic can hold it.
     *
     * It changed shape when the curated catalog did, and it had to: naming an expression
     * that no longer exists would leave this test passing while guarding nothing. What it
     * used to say — `CurrencyCatalog.reduce(localeCurrencyCode())` — reduced the device's
     * answer to a curated list; there is no list any more, so the filter *is* the premise,
     * and the premise is what the two deciders share, textually.
     */
    private val theOneResolver = Regex(
        """localeCurrencyCode\(\)\s*\?\.uppercase\(\)\s*""" +
            """\?\.takeIf \{ it\.isNotBlank\(\) && isTwoDecimalCurrency\(it\) }"""
    )

    private val expectedDeciders = setOf(
        // The account a brand-new install starts with no longer appears here: it
        // reads the seeded base currency instead of resolving one of its own, which
        // is the whole point of 9.1 — one resolution, not two that can disagree.
        // `CreateAccountUseCase` no longer appears either: its currency parameter lost
        // its default when the form began stating one, so it decides nothing.
        // `CreditCardRepository` is gone from this set for the same reason as the first
        // two: the currency a new card is pre-selected with is now the seeded base, not
        // the region read again. The region does resolve it — once, on the first run —
        // and reading it live here was a second answer that parted company with the
        // first the moment the user travelled.
        // `DashboardPreviewFactory` left for that same reason, last: the fabricated
        // accounts of a component preview have to look like the app they preview, and
        // what the app pre-selects is the seeded base — so reading the region live here
        // was a third answer, and the one most visible, since the preview sits a
        // dashboard row away from the real cards.
        //
        // The base currency of consolidation — the *other* thing the locale resolves
        // (design D28). It denominates no account, so it does not widen the window
        // this test closes; it is here because it uses the same one resolver, and a
        // second way of deciding must fail whichever of the two it decides.
        "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/database/repository/BaseCurrencyRepository.kt",
        // The seeding of the currency registry — the *third* thing the locale answers,
        // and the newest. It creates no account either: it writes the row that makes the
        // device's currency exist at all, which is what lets the base above resolve to
        // it instead of falling back. It uses the same one expression deliberately, so
        // that the row seeded and the base resolved can never disagree about which
        // currency the device named.
        "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/model/CurrencySeed.kt",
    )

    @Test
    fun `every production site that decides a currency decides it the one way`() {
        val found = productionSources
            .filter { theOneResolver.containsMatchIn(it.readText()) }
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
     *
     * The detector names **both spellings** the app uses for the one idea. It used to
     * name only the shared sheet's callback, which quietly meant "chooses a currency
     * *through `CurrencyPickerModal`*" — so the rate form dropped out of this set the
     * moment its field became the dropdown every other selector of this app is, without
     * having stopped choosing a currency for one instant. A guard that a change of
     * widget can switch off is not guarding the rule it is named after.
     */
    @Test
    fun `exactly two forms let the user choose an account's currency`() {
        // `SelectFrom`/`SelectTo` are the rate form's two ends, which are currencies
        // chosen and therefore in scope for this guard exactly as `SelectCurrency` was.
        val choosesACurrency = Regex("""CurrencySelected|SelectCurrency|SelectFrom|SelectTo""")

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
            // Not a third door either: the base currency is a **display preference**,
            // chosen over the whole catalog and creating no account. What it changes is
            // which currency a consolidated figure reads in — reversibly, since nothing
            // converted is persisted — and never what an account is denominated in.
            "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/settings/SettingsScreen.kt",
            // Not a third door: the denomination of a budget limit, chosen among the
            // currencies the user already holds, creating none.
            "feature/budgets/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/budgetForm/BudgetFormAction.kt",
            "feature/budgets/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/budgetForm/BudgetFormModal.kt",
            "feature/budgets/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/budgetForm/BudgetFormViewModel.kt",
            // The rate form picks which currency a rate is *about* — an observation, not
            // an account.
            "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/exchangeRateForm/ExchangeRateFormAction.kt",
            "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/exchangeRateForm/ExchangeRateFormModal.kt",
            "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/exchangeRateForm/ExchangeRateFormViewModel.kt",
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
