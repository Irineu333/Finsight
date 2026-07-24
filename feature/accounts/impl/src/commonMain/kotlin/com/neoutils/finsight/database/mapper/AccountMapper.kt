package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.database.mapper.toDomain
import com.neoutils.finsight.database.mapper.toEntity

class AccountMapper {
    fun toDomain(entity: AccountEntity): Account {
        return Account(
            id = entity.id,
            name = entity.name,
            type = entity.type.toDomain(),
            currency = entity.currency,
            iconKey = entity.iconKey,
            isDefault = entity.isDefault,
            createdAt = entity.createdAt,
            isArchived = entity.isArchived,
        )
    }

    fun toEntity(domain: Account): AccountEntity {
        return AccountEntity(
            id = domain.id,
            name = domain.name,
            type = domain.type.toEntity(),
            currency = domain.currency,
            iconKey = domain.iconKey,
            isDefault = domain.isDefault,
            createdAt = domain.createdAt,
            isArchived = domain.isArchived,
        )
    }

}
