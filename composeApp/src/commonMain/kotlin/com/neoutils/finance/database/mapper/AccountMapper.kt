package com.neoutils.finance.database.mapper

import com.neoutils.finance.database.entity.AccountEntity
import com.neoutils.finance.domain.model.Account

class AccountMapper {
    fun toDomain(entity: AccountEntity): Account {
        return Account(
            id = entity.id,
            name = entity.name,
            isDefault = entity.isDefault,
            createdAt = entity.createdAt
        )
    }

    fun toEntity(domain: Account): AccountEntity {
        return AccountEntity(
            id = domain.id,
            name = domain.name,
            isDefault = domain.isDefault,
            createdAt = domain.createdAt
        )
    }
}
