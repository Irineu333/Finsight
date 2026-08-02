package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.ExchangeRateEntity
import com.neoutils.finsight.domain.model.ExchangeRate

class ExchangeRateMapper {

    fun toDomain(entity: ExchangeRateEntity) = ExchangeRate(
        id = entity.id,
        currency = entity.currency,
        counterCurrency = entity.counterCurrency,
        date = entity.date,
        rate = entity.rate,
        source = when (entity.source) {
            ExchangeRateEntity.Source.DERIVED -> ExchangeRate.Source.DERIVED
            ExchangeRateEntity.Source.USER -> ExchangeRate.Source.USER
        },
    )

    fun toEntity(domain: ExchangeRate) = ExchangeRateEntity(
        id = domain.id,
        currency = domain.currency,
        counterCurrency = domain.counterCurrency,
        date = domain.date,
        rate = domain.rate,
        source = when (domain.source) {
            ExchangeRate.Source.DERIVED -> ExchangeRateEntity.Source.DERIVED
            ExchangeRate.Source.USER -> ExchangeRateEntity.Source.USER
        },
    )
}
