package com.neoutils.finsight.auth

import com.neoutils.finsight.domain.auth.AuthService
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth

internal class FirebaseAuthService : AuthService {
    override suspend fun getUserId(): String? {
        val auth = Firebase.auth

        if (auth.currentUser == null) {
            return auth.signInAnonymously().user?.uid
        }

        return auth.currentUser?.uid
    }
}
