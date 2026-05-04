package com.neoutils.finsight.feature.accounts.mapper

import com.neoutils.finsight.core.database.entity.AccountEntity
import com.neoutils.finsight.feature.accounts.model.Account

class AccountMapper {
    fun toDomain(entity: AccountEntity): Account {
        return Account(
            id = entity.id,
            name = entity.name,
            iconKey = entity.iconKey,
            isDefault = entity.isDefault,
            createdAt = entity.createdAt
        )
    }

    fun toEntity(domain: Account): AccountEntity {
        return AccountEntity(
            id = domain.id,
            name = domain.name,
            iconKey = domain.iconKey,
            isDefault = domain.isDefault,
            createdAt = domain.createdAt
        )
    }
}
