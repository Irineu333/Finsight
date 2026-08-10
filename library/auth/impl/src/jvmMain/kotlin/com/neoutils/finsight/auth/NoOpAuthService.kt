package com.neoutils.finsight.auth

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.domain.auth.AuthService
import com.neoutils.finsight.domain.error.AuthError

class NoOpAuthService : AuthService {
    override suspend fun getUserId(): Either<AuthError, String?> = null.right()
}
