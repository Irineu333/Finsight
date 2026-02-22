package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.CreditCardEntity
import com.neoutils.finsight.domain.model.CreditCard

class CreditCardMapper {
    fun toDomain(entity: CreditCardEntity): CreditCard {
        return CreditCard(
            id = entity.id,
            name = entity.name,
            limit = entity.limit,
            closingDay = entity.closingDay,
            dueDay = entity.dueDay,
            createdAt = entity.createdAt
        )
    }

    fun toEntity(domain: CreditCard): CreditCardEntity {
        return CreditCardEntity(
            id = domain.id,
            name = domain.name,
            limit = domain.limit,
            closingDay = domain.closingDay,
            dueDay = domain.dueDay,
            createdAt = domain.createdAt
        )
    }
}
