package com.neoutils.finsight.auth

import com.neoutils.finsight.domain.auth.AuthService

internal class NoOpAuthService : AuthService {
    override suspend fun getUserId(): String? = null
}
