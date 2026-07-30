package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.ExchangeRateDao
import com.neoutils.finsight.database.mapper.ExchangeRateMapper
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * The rate archive over the `exchange_rates` table.
 *
 * The reading policy — *the last rate on or before the date, the user's winning over a
 * derived one of the same date* — is not re-stated here: it is the DAO's query, in one
 * place, and this class only maps.
 */
class ExchangeRateRepository(
    private val dao: ExchangeRateDao,
    private val mapper: ExchangeRateMapper,
) : IExchangeRateRepository {

    override suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate? {
        return dao.rateAsOf(currency, date)?.let(mapper::toDomain)
    }

    override suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate> {
        return dao.ratesAsOf(date).associate { it.currency to mapper.toDomain(it) }
    }

    override fun observeAll(): Flow<List<ExchangeRate>> {
        return dao.observeAll().map { rates -> rates.map(mapper::toDomain) }
    }

    override suspend fun save(rate: ExchangeRate) {
        // One write, not two. `REPLACE` over the unique `(currency, date, source)` is
        // what makes registering and correcting the same operation: a correction is a
        // `USER` row that coexists with the `DERIVED` one it outranks instead of
        // destroying the observation the operation itself made.
        dao.insert(mapper.toEntity(rate))
    }

    override suspend fun remove(rate: ExchangeRate) {
        dao.deleteById(rate.id)
    }
}
