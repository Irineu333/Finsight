package com.neoutils.finsight.feature.accounts.api

import com.neoutils.finsight.navigation.NavRoute
import kotlinx.serialization.Serializable

@Serializable
data class AccountsRoute(val accountId: Long? = null) : NavRoute
