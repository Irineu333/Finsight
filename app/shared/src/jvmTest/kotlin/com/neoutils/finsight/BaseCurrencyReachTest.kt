package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **The base currency appears only where a consolidation happened** — design D29.
 *
 * Where the ledger answered in one currency (an account balance, an invoice owed, a
 * statement line, an instalment), the figure is shown in *that* currency and the base
 * does not appear. This is what the whole per-currency read surface exists for, and it
 * needs a guard of its own because **the violation is invisible in the common case**:
 * with every account in the base, showing the base and showing the account's own
 * currency produce exactly the same text. A balance wired to the base by mistake passes
 * every test, every review and every use — until somebody creates a dollar account and
 * sees it in reais, with no conversion, just the wrong symbol.
 *
 * The plan even *creates* the opportunity: the reducer legitimately needs the base, and
 * the two uses sit a line apart looking identical. So the reach of
 * `IBaseCurrencyRepository` is pinned by name, and the list is short on purpose.
 */
class BaseCurrencyReachTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val productionSources: List<File> = repoRoot.walkTopDown()
        .onEnter { it.name != "build" && it.name != ".git" }
        .filter { it.isFile && it.extension == "kt" }
        .filter { file ->
            val path = file.relativeTo(repoRoot).invariantSeparatorsPath
            "/src/" in path && Regex("/src/[a-zA-Z]*Main/") in path
        }
        .toList()

    private val allowed = setOf(
        // The declaration itself.
        "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/repository/IBaseCurrencyRepository.kt",
        // The consolidation layer: the reducer that expresses a multi-currency figure in
        // the base, the rate harvest that stores rates *against* the base, and the
        // trigger a consolidated figure recomputes on.
        "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/ConsolidateMoneyUseCase.kt",
        "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/HarvestExchangeRateUseCase.kt",
        "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/ObserveConsolidationChangesUseCase.kt",
        // What the archive implies the other end of a crossing is worth. It names the
        // base for the same reason the harvest does — rates are stored *against* it, so
        // the base side is the one with no rate of its own — and it denominates nothing:
        // the currencies it converts between are the two accounts', never the base's.
        "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/SuggestCrossCurrencyAmountUseCase.kt",
        // The account a brand-new install starts with. The base is a **pre-selection**
        // here, which the spec allows in as many words — it is not denominating a figure,
        // it is answering "what currency should this first account be created in".
        "feature/accounts/api/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/EnsureDefaultAccountUseCase.kt",
        // The account form: the base is the currency a **new** account is
        // pre-selected with, exactly as it is for the account a fresh install starts
        // with. It denominates nothing — what the account ends up in is whatever the
        // user leaves in the row, and from then on the account states it itself.
        "feature/accounts/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/accountForm/AccountFormViewModel.kt",
        // The settings feature owns the preference: it implements it, states it, and
        // names it as the direction rates are stored in.
        "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/database/repository/BaseCurrencyRepository.kt",
        "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/di/SettingsModule.kt",
        "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/exchangeRateForm/ExchangeRateFormViewModel.kt",
        "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/exchangeRates/ExchangeRatesViewModel.kt",
        "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/settings/SettingsViewModel.kt",
    )

    @Test
    fun `the base currency is reached only by the consolidation layer and by settings`() {
        val found = productionSources
            .filter { "IBaseCurrencyRepository" in it.readText() }
            .map { it.relativeTo(repoRoot).invariantSeparatorsPath }
            .toSet()

        assertEquals(
            allowed,
            found,
            "A production site outside the consolidation layer reads the base currency. " +
                "It is almost always the same mistake: a figure the ledger answered in " +
                "one currency being denominated by the base because the site did not " +
                "know what else to say. The currency of a figure is the figure's own.\n" +
                (found - allowed).joinToString("\n") { "  NEW: $it" } +
                (allowed - found).joinToString("\n") { "  GONE: $it — the list is out of date" },
        )
    }
}
