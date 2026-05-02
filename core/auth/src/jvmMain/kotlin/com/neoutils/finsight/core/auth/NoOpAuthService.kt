package com.neoutils.finsight.core.auth

import com.neoutils.finsight.core.auth.AuthService

internal class NoOpAuthService : AuthService {
    override suspend fun getUserId(): String? = null
}
