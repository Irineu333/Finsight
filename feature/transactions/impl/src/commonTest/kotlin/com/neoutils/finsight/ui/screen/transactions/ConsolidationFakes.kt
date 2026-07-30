package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.extension.MoneyFigure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate

/**
 * No rate at all — the single-currency profile every case in this package exercises, where
 * consolidation passes one currency straight through, exact.
 */
internal object NoRates : IExchangeRateRepository {
    override suspend fun rateOn(currency: String, date: LocalDate) = null
    override fun observeAll() = throw NotImplementedError()
    override suspend fun getAll() = throw NotImplementedError()
    override suspend fun record(rate: ExchangeRate) = throw NotImplementedError()
    override suspend fun remove(rate: ExchangeRate) = throw NotImplementedError()
}

internal object FixedBase : IBaseCurrencyRepository {
    private val state = MutableStateFlow("BRL")
    override fun observe() = state
    override suspend fun set(currency: String) = throw NotImplementedError()
}

/**
 * The one number behind a single-currency figure. Every summary here is in one currency, so
 * the figure has exactly one term and asserting on it is asserting on what the card shows.
 */
internal val MoneyFigure.amount: Double get() = terms.single().value

/** The reading policy of that same single term. */
internal val MoneyFigure.policyOfSingleTerm get() = terms.single().policy
