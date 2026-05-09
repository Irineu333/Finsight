@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.accounts.model.form

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.core.ui.util.AppIcon
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class AccountForm(
    val id: Long = 0,
    val name: String = "",
    val icon: AppIcon = AppIcon.WALLET,
    val isDefault: Boolean = false,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
) {
    fun build(): Account {
        return Account(
            id = id,
            name = name,
            iconKey = icon.key,
            isDefault = isDefault,
            createdAt = createdAt,
        )
    }
}
