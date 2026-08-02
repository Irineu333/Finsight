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
        // The rate archive, and the one place that may know at once what is stored and
        // which preference is in force. `IExchangeRateRepository` has always promised
        // rates *against the base*; here that stops being true by accident — there used
        // to be only one base — and becomes true by construction (design D4). The
        // dependency replaces an assumption that was implicit before, and it denominates
        // no figure: what it answers is a rate, never an amount.
        "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/database/repository/ExchangeRateRepository.kt",
        // The consolidation layer: the reducer that expresses a multi-currency figure in
        // the base, and the trigger a consolidated figure recomputes on.
        //
        // The harvest and the cross-currency suggestion used to be here and are not any
        // more, which is the shape of this change: with the pair explicit, both speak
        // about the two currencies of the operation in front of them and neither has any
        // reason to know which preference is in force.
        "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/ConsolidateMoneyUseCase.kt",
        "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/ObserveConsolidationChangesUseCase.kt",
        // The account a brand-new install starts with. The base is a **pre-selection**
        // here, which the spec allows in as many words — it is not denominating a figure,
        // it is answering "what currency should this first account be created in".
        "feature/accounts/api/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/EnsureDefaultAccountUseCase.kt",
        // The account form: the base is the currency a **new** account is
        // pre-selected with, exactly as it is for the account a fresh install starts
        // with. It denominates nothing — what the account ends up in is whatever the
        // user leaves in the row, and from then on the account states it itself.
        "feature/accounts/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/accountForm/AccountFormViewModel.kt",
        // The card form's pre-selection, by way of `currencyForNewCard`. Same role as
        // the two above and denominating nothing: a card being created has no account
        // to read a currency from, and what is written is whatever `insert` is given.
        // It used to answer this by reading the device's region **live**, which is the
        // failure this list is shaped to catch from the other side — not a figure wearing
        // the base by mistake, but the base itself resolved a second time, by a route
        // that parts company with the seeded one the moment the user travels.
        "feature/creditcards/impl/src/commonMain/kotlin/com/neoutils/finsight/database/repository/CreditCardRepository.kt",
        // The dashboard's component preview, denominating accounts it fabricates. Same
        // role and same history as the card form: it read the locale live, which put `$`
        // on a preview sitting one row from real cards reading `R$`. A preview has to
        // look like the app, and what the app pre-selects is this.
        "feature/dashboard/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/dashboard/DashboardPreviewFactory.kt",
        // --- The tie-break between the two ends of a cross-currency operation ---
        //
        // These four are the surfaces that show an operation with **no perspective**, and
        // they name the base for a reason that is the opposite of what this list hunts.
        // The failure mode above is a figure the ledger answered in one currency being
        // *denominated* by the base. Here nothing is denominated by the base and nothing
        // is converted: an operation that crossed currencies has **two** figures, both
        // exact and both the ledger's own — US$ 550,00 left and R$ 500,00 of an invoice
        // was paid — and the base only decides which of the two is stated
        // (`Transaction.figureLegUnder`). Where neither end is in the base, the reading
        // is unchanged; the base is never a fallback and never a resort.
        //
        // They are four rather than one because a card and the detail it opens MUST NOT
        // answer with different money (`presentation-mapping`), so every neutral surface
        // consumes the same owner. A surface that names an account is absent from this
        // list on purpose: its figure is that account's line, whatever the base.
        "feature/transactions/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/viewTransaction/ViewTransactionViewModel.kt",
        "feature/transactions/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/transactions/TransactionsViewModel.kt",
        "feature/dashboard/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/dashboard/DashboardViewModel.kt",
        "feature/report/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/report/viewer/ReportViewerViewModel.kt",
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
