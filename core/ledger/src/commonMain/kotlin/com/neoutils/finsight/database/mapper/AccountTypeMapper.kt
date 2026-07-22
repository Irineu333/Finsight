package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.AccountType

/**
 * The chart-of-accounts type, across the storage boundary.
 *
 * Both sides are the same closed set, so this is a pure renaming — which is
 * exactly why it must exist once. Three private copies of a total `when` drift
 * silently: nothing fails until a sixth type is added to one of them.
 */
fun AccountEntity.Type.toDomain(): AccountType = when (this) {
    AccountEntity.Type.ASSET -> AccountType.ASSET
    AccountEntity.Type.LIABILITY -> AccountType.LIABILITY
    AccountEntity.Type.INCOME -> AccountType.INCOME
    AccountEntity.Type.EXPENSE -> AccountType.EXPENSE
    AccountEntity.Type.EQUITY -> AccountType.EQUITY
}

fun AccountType.toEntity(): AccountEntity.Type = when (this) {
    AccountType.ASSET -> AccountEntity.Type.ASSET
    AccountType.LIABILITY -> AccountEntity.Type.LIABILITY
    AccountType.INCOME -> AccountEntity.Type.INCOME
    AccountType.EXPENSE -> AccountEntity.Type.EXPENSE
    AccountType.EQUITY -> AccountEntity.Type.EQUITY
}
