package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import kotlinx.datetime.LocalDate

/**
 * No rate at all — the single-currency profile every dashboard case here exercises, where
 * consolidation passes one currency straight through, exact.
 */
internal object NoRates : IExchangeRateRepository {
    override suspend fun rateOn(currency: String, date: LocalDate) = null
    override fun observeAll() = throw NotImplementedError()
    override suspend fun getAll() = throw NotImplementedError()
    override suspend fun record(rate: ExchangeRate) = throw NotImplementedError()
    override suspend fun remove(rate: ExchangeRate) = throw NotImplementedError()
}
