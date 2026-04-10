package com.neoutils.finsight.auth

import com.neoutils.finsight.domain.auth.AuthService

class NoOpAuthService : AuthService {
    override suspend fun getUserId(): String? = null
}
