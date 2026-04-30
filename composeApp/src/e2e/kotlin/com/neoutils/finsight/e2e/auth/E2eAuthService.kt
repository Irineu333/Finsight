package com.neoutils.finsight.e2e.auth

import com.neoutils.finsight.domain.auth.AuthService

class E2eAuthService : AuthService {
    override suspend fun getUserId(): String = E2E_USER_ID

    companion object {
        const val E2E_USER_ID = "e2e-user"
    }
}
