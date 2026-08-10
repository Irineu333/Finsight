package com.neoutils.finsight.auth

import arrow.core.Either
import com.neoutils.finsight.domain.auth.AuthService
import com.neoutils.finsight.domain.error.AuthError
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth

class FirebaseAuthService : AuthService {

    override suspend fun getUserId(): Either<AuthError, String?> = Either.catch {
        val auth = Firebase.auth

        if (auth.currentUser == null) {
            return@catch auth.signInAnonymously().user?.uid
        }

        auth.currentUser?.uid
    }.mapLeft(::AuthError)
}
