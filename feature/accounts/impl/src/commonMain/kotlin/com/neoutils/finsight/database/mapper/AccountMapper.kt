package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType

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

    private fun AccountEntity.Type.toDomain() = when (this) {
        AccountEntity.Type.ASSET -> AccountType.ASSET
        AccountEntity.Type.LIABILITY -> AccountType.LIABILITY
        AccountEntity.Type.INCOME -> AccountType.INCOME
        AccountEntity.Type.EXPENSE -> AccountType.EXPENSE
        AccountEntity.Type.EQUITY -> AccountType.EQUITY
    }

    private fun AccountType.toEntity() = when (this) {
        AccountType.ASSET -> AccountEntity.Type.ASSET
        AccountType.LIABILITY -> AccountEntity.Type.LIABILITY
        AccountType.INCOME -> AccountEntity.Type.INCOME
        AccountType.EXPENSE -> AccountEntity.Type.EXPENSE
        AccountType.EQUITY -> AccountEntity.Type.EQUITY
    }
}
