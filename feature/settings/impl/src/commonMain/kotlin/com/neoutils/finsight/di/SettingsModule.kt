package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.ExchangeRateMapper
import com.neoutils.finsight.database.repository.BaseCurrencyRepository
import com.neoutils.finsight.database.repository.CurrencyRepository
import com.neoutils.finsight.database.repository.ExchangeRateRepository
import com.neoutils.finsight.database.repository.RateSyncStateRepository
import com.neoutils.finsight.database.repository.RepositoryCurrencySymbols
import com.neoutils.finsight.domain.model.CurrencySeeding
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.PlatformCurrencySeeding
import com.neoutils.finsight.domain.model.SeededBaseCurrency
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.ICurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.IRateSyncStateRepository
import com.neoutils.finsight.domain.repository.IRemoteRateSource
import com.neoutils.finsight.domain.usecase.ArchiveCurrencyUseCase
import com.neoutils.finsight.domain.usecase.DeleteCurrencyUseCase
import com.neoutils.finsight.domain.usecase.SaveCurrencyUseCase
import com.neoutils.finsight.extension.CurrencySymbols
import com.neoutils.finsight.domain.model.CurrencyInfo
import com.neoutils.finsight.ui.modal.archiveCurrency.ArchiveCurrencyViewModel
import com.neoutils.finsight.ui.modal.currencyForm.CurrencyFormViewModel
import com.neoutils.finsight.ui.modal.deleteCurrency.DeleteCurrencyViewModel
import com.neoutils.finsight.ui.modal.viewCurrency.ViewCurrencyViewModel
import com.neoutils.finsight.ui.modal.exchangeRateForm.ExchangeRateFormViewModel
import com.neoutils.finsight.ui.screen.currencies.CurrenciesViewModel
import com.neoutils.finsight.ui.screen.exchangeRateHistory.ExchangeRateHistoryViewModel
import com.neoutils.finsight.ui.screen.exchangeRates.ExchangeRatesViewModel
import com.neoutils.finsight.network.FrankfurterRateSource
import com.neoutils.finsight.ui.screen.settings.SettingsViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The settings feature owns the **implementations** of the two repositories the
 * consolidation layer declares in `:core:model`. The split is a compilation
 * constraint: `feature/accounts/api` and `:core:model` both need the base currency,
 * and `api ⊄ api` while `core ⊄ feature`. The shell binds this module, so the star
 * topology holds.
 */
val settingsModule = module {

    single<IBaseCurrencyRepository> { BaseCurrencyRepository(settings = get()) }

    // The migration's backfill, resolved where the preference is visible and asked for
    // by `core/database`, which may name neither `Settings` nor the repository. There is
    // no DI cycle: `BaseCurrencyRepository` depends only on `Settings`, never on
    // `AppDatabase`, and that is what lets the migration read it while it is opening.
    single<SeededBaseCurrency> { SeededBaseCurrency { get<IBaseCurrencyRepository>().observe().value } }

    // The seeding, resolved where the platform and the locale are visible and asked
    // for by `core/database`, which may name neither — the same move `SeededBaseCurrency`
    // above already makes, and for the same reason.
    single<CurrencySeeding> { PlatformCurrencySeeding() }

    single<ICurrencyRepository> {
        CurrencyRepository(database = get(), dao = get(), exchangeRateDao = get())
    }

    // The port of `:core:common`: the only binding where the table and the composition
    // local meet, so that `:core:designsystem` never has to see `:core:model`.
    single<CurrencySymbols> { RepositoryCurrencySymbols(repository = get()) }

    factory { SaveCurrencyUseCase(repository = get()) }
    factory {
        DeleteCurrencyUseCase(
            repository = get(),
            exchangeRateRepository = get(),
            rateSyncStateRepository = get(),
            accountDao = get(),
            budgetDao = get(),
        )
    }
    factory {
        ArchiveCurrencyUseCase(
            repository = get(),
            baseCurrencyRepository = get(),
        )
    }

    factory { ExchangeRateMapper() }

    // The concrete type is bound as well, and the interface resolves **from it**, so
    // there is one instance and not two. The rates screens reach `observeInForce()`
    // through this binding: it is a read only they make, and putting it on
    // `IExchangeRateRepository` would oblige the thirteen fakes that implement the
    // interface to answer a question their modules never ask.
    single { ExchangeRateRepository(dao = get(), mapper = get(), baseCurrencyRepository = get()) }
    single<IExchangeRateRepository> { get<ExchangeRateRepository>() }

    single<IRateSyncStateRepository> { RateSyncStateRepository(settings = get()) }

    // The one HTTP client of the app, built here because this is the one module that may
    // hold one (design D11). A `:core:network` was rejected precisely so the restriction
    // would be the module graph rather than discipline. The engine comes from each
    // platform's classpath, so this stays in `commonMain`.
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
    single<IRemoteRateSource> { FrankfurterRateSource(client = get()) }

    viewModel {
        SettingsViewModel(
            baseCurrencyRepository = get(),
            currencyRepository = get(),
            analytics = get(),
        )
    }

    viewModel {
        ExchangeRatesViewModel(
            baseCurrencyRepository = get(),
            exchangeRateRepository = get<ExchangeRateRepository>(),
            rateSyncStateRepository = get(),
            getAccountCurrencies = get(),
        )
    }

    viewModel {
        ExchangeRateHistoryViewModel(
            initialCurrency = it.getOrNull<String>(),
            exchangeRateRepository = get(),
        )
    }

    viewModel {
        ExchangeRateFormViewModel(
            existing = it.getOrNull<ExchangeRate>(),
            baseCurrencyRepository = get(),
            exchangeRateRepository = get(),
            currencyRepository = get(),
            modalManager = get(),
            analytics = get(),
        )
    }

    viewModel {
        CurrenciesViewModel(
            currencyRepository = get(),
            baseCurrencyRepository = get(),
        )
    }

    viewModel {
        ViewCurrencyViewModel(
            code = it.get(),
            currencyRepository = get(),
            baseCurrencyRepository = get(),
            deleteCurrency = get(),
            archiveCurrency = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }

    viewModel {
        DeleteCurrencyViewModel(
            code = it.get(),
            deleteCurrency = get(),
            modalManager = get(),
            analytics = get(),
        )
    }

    viewModel {
        ArchiveCurrencyViewModel(
            code = it.get(),
            archiveCurrency = get(),
            modalManager = get(),
            analytics = get(),
        )
    }

    viewModel {
        CurrencyFormViewModel(
            existing = it.getOrNull<CurrencyInfo>(),
            saveCurrency = get(),
            modalManager = get(),
            analytics = get(),
        )
    }
}
