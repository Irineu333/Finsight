package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.Account

/**
 * A chart-of-accounts row across the storage boundary.
 *
 * Closure travels with it: a leg that dropped the flag would report every archived
 * account as open, and every rule derived from it would go quiet.
 */
fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    type = type.toDomain(),
    currency = currency,
    iconKey = iconKey,
    isDefault = isDefault,
    createdAt = createdAt,
    isArchived = isArchived,
)
