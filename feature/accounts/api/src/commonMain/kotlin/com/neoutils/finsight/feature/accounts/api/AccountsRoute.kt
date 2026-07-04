package com.neoutils.finsight.feature.accounts.api

import kotlinx.serialization.Serializable

@Serializable
data class AccountsRoute(val accountId: Long? = null)
