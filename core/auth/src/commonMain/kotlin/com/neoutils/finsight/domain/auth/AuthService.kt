package com.neoutils.finsight.domain.auth

import arrow.core.Either
import com.neoutils.finsight.domain.error.AuthError

interface AuthService {

    /**
     * The anonymous id this install is known by, or [AuthError] when the platform could
     * not produce one.
     *
     * Failing is ordinary here — it takes a round trip and a keychain — so it is stated in
     * the return type rather than thrown. A caller that has to remember to guard is a
     * caller that will forget, and on iOS forgetting aborts the process.
     */
    suspend fun getUserId(): Either<AuthError, String?>
}
