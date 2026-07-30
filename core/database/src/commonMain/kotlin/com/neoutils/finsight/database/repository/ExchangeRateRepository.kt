package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.ExchangeRateDao
import com.neoutils.finsight.database.entity.ExchangeRateEntity
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * The rate history, over the local table and nothing else. The policy — the last rate on or
 * before the date, the user's own winning on the same day — lives in the query, so there is
 * one place where "which rate governs this figure" is answered.
 */
class ExchangeRateRepository(
    private val exchangeRateDao: ExchangeRateDao,
) : IExchangeRateRepository {

    override suspend fun rateOn(currency: String, date: LocalDate): ExchangeRate? =
        exchangeRateDao.rateOn(currency, date)?.toDomain()

    override fun observeAll(): Flow<List<ExchangeRate>> =
        exchangeRateDao.observeAll().map { rates -> rates.map { it.toDomain() } }

    override suspend fun getAll(): List<ExchangeRate> = exchangeRateDao.getAll().map { it.toDomain() }

    override suspend fun record(rate: ExchangeRate) = exchangeRateDao.upsert(rate.toEntity())

    private fun ExchangeRateEntity.toDomain() = ExchangeRate(
        currency = currency,
        date = date,
        rate = rate,
        source = when (source) {
            ExchangeRateEntity.Source.OPERATION -> ExchangeRate.Source.OPERATION
            ExchangeRateEntity.Source.USER -> ExchangeRate.Source.USER
        },
    )

    private fun ExchangeRate.toEntity() = ExchangeRateEntity(
        currency = currency,
        date = date,
        rate = rate,
        source = when (source) {
            ExchangeRate.Source.OPERATION -> ExchangeRateEntity.Source.OPERATION
            ExchangeRate.Source.USER -> ExchangeRateEntity.Source.USER
        },
    )
}
