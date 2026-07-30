package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.CreditCardEntity
import com.neoutils.finsight.domain.model.CreditCard

class CreditCardMapper {
    fun toDomain(row: com.neoutils.finsight.database.dao.CreditCardWithArchival): CreditCard =
        toDomain(row.creditCard, row.currency).copy(isArchived = row.isArchived)

    /**
     * [currency] is a parameter and not a column of the card's own table: the card is a facade
     * over a `LIABILITY` row of the chart of accounts, and that row is where a currency is
     * decided and stored. Requiring it here is what stops a card being mapped without one.
     */
    fun toDomain(entity: CreditCardEntity, currency: String): CreditCard {
        return CreditCard(
            id = entity.id,
            name = entity.name,
            limit = entity.limit,
            closingDay = entity.closingDay,
            dueDay = entity.dueDay,
            iconKey = entity.iconKey,
            createdAt = entity.createdAt,
            accountId = entity.accountId,
            currency = currency,
        )
    }

    fun toEntity(domain: CreditCard): CreditCardEntity {
        return CreditCardEntity(
            id = domain.id,
            name = domain.name,
            limit = domain.limit,
            closingDay = domain.closingDay,
            dueDay = domain.dueDay,
            iconKey = domain.iconKey,
            createdAt = domain.createdAt,
            accountId = domain.accountId,
        )
    }
}
