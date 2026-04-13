package com.neoutils.finsight.auth

import com.neoutils.finsight.domain.auth.AuthService
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth

class FirebaseAuthService : AuthService {
    override suspend fun getUserId(): String? {
        val auth = Firebase.auth
        if (auth.currentUser == null) auth.signInAnonymously()
        return auth.currentUser?.uid
    }
}
