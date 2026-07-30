package com.neoutils.finsight.database.repository

import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.usecase.ResolveBaseCurrencyUseCase
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The base currency, resolved from the locale on first run and persisted from then on.
 *
 * Resolution happens **in construction, and only when nothing is stored** — which is what
 * makes it once-and-for-all without a startup step to remember to call, and what covers the
 * already-installed app, where no account is created and so no account creation could carry
 * it. Reading the stored value first is also what makes a later change of locale a no-op:
 * the locale is consulted exactly when there is no answer yet.
 *
 * Being resolved eagerly matters for a second reason: the binding is a `single`, so the
 * first figure the app renders already reads a settled base, rather than a placeholder that
 * corrects itself a frame later.
 */
class BaseCurrencyRepository(
    private val settings: Settings,
    resolveBaseCurrency: ResolveBaseCurrencyUseCase,
) : IBaseCurrencyRepository {

    private val base = MutableStateFlow(
        settings.getStringOrNull(KEY) ?: resolveBaseCurrency().also { settings.putString(KEY, it) }
    )

    override fun observe(): StateFlow<String> = base

    override suspend fun set(currency: String) {
        settings.putString(KEY, currency)
        base.value = currency
    }

    companion object {
        private const val KEY = "base_currency"
    }
}
