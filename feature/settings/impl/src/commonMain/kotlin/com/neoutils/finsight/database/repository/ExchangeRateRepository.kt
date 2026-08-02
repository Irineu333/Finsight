package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.ExchangeRateDao
import com.neoutils.finsight.database.mapper.ExchangeRateMapper
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * The rate archive over the `exchange_rates` table, and **the owner of the declared
 * precedence**.
 *
 * Two policies meet here and they are deliberately not the same one:
 *
 * - *the last observation on or before the date, per pair, the user's over the derived
 *   one* is the DAO's query, stated once, in SQL;
 * - *direct ▸ inverse ▸ one pivot* is [resolveRate], pure and over what that query
 *   returned.
 *
 * [IExchangeRateRepository] has always promised rates **against the base**. That used to
 * be true by accident — there was only ever one base, and every row was on its axis.
 * Here it becomes true by construction, which is why `ConsolidateMoneyUseCase`, the view
 * models and every screen that shows a figure are untouched by the switch existing
 * (design D4).
 *
 * The dependency on [IBaseCurrencyRepository] is what pays for that, and it is the one
 * legitimate place for it: this is the only point that can know at once what is stored
 * and which preference is in force. It replaces an assumption that used to be implicit,
 * and it denominates no figure.
 */
class ExchangeRateRepository(
    private val dao: ExchangeRateDao,
    private val mapper: ExchangeRateMapper,
    private val baseCurrencyRepository: IBaseCurrencyRepository,
) : IExchangeRateRepository {

    override suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate? {
        return rateBetween(currency, baseCurrencyRepository.observe().value, date)
    }

    override suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate> {
        val base = baseCurrencyRepository.observe().value
        val observations = dao.ratesAsOf(date).map(mapper::toDomain)

        return observations
            .flatMap { listOf(it.currency, it.counterCurrency) }
            .distinct()
            .filter { it != base }
            .mapNotNull { currency ->
                observations.resolveRate(currency, base)?.let { currency to answer(currency, base, date, it) }
            }
            .toMap()
    }

    override suspend fun rateBetween(from: String, to: String, date: LocalDate): ExchangeRate? {
        // The direct level short-circuits to a single indexed query, which is the common
        // case by a wide margin: it answers with the stored row itself, id and origin
        // included, instead of the derived answer below.
        dao.rateOfPairAsOf(from, to, date)?.let { return mapper.toDomain(it) }

        val resolved = dao.ratesAsOf(date).map(mapper::toDomain).resolveRate(from, to) ?: return null
        return answer(from, to, date, resolved)
    }

    /**
     * A rate the archive **implies** rather than holds.
     *
     * `id = 0` because it is no row: it was read out of the observations, not stored
     * beside them, and offering it an id would invite something to write it back. The
     * origin is [ExchangeRate.Source.DERIVED] for the same reason — nobody typed it.
     */
    private fun answer(from: String, to: String, date: LocalDate, rate: Double) = ExchangeRate(
        currency = from,
        counterCurrency = to,
        date = date,
        rate = rate,
        source = ExchangeRate.Source.DERIVED,
    )

    override fun observeAll(): Flow<List<ExchangeRate>> {
        return dao.observeAll().map { rates -> rates.map(mapper::toDomain) }
    }

    override suspend fun save(rate: ExchangeRate) {
        // One write, not two. `REPLACE` over the unique
        // `(currency, counterCurrency, date, source)` is what makes registering and
        // correcting the same operation: a correction is a `USER` row that coexists with
        // the `DERIVED` one it outranks instead of destroying the observation the
        // operation itself made.
        dao.insert(mapper.toEntity(rate))
    }

    override suspend fun remove(rate: ExchangeRate) {
        dao.deleteById(rate.id)
    }
}
