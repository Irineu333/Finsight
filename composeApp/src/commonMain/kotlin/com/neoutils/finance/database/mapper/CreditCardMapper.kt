package com.neoutils.finance.database.mapper

import com.neoutils.finance.database.entity.CreditCardEntity
import com.neoutils.finance.domain.model.CreditCard

class CreditCardMapper {
    fun toDomain(entity: CreditCardEntity): CreditCard {
        return CreditCard(
                id = entity.id,
                name = entity.name,
                limit = entity.limit,
                closingDay = entity.closingDay,
                createdAt = entity.createdAt
        )
    }

    fun toEntity(domain: CreditCard): CreditCardEntity {
        return CreditCardEntity(
                id = domain.id,
                name = domain.name,
                limit = domain.limit,
                closingDay = domain.closingDay,
                createdAt = domain.createdAt
        )
    }
}
