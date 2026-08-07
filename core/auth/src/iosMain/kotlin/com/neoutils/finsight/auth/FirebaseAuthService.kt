package com.neoutils.finsight.auth

import com.neoutils.finsight.domain.auth.AuthService
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlin.coroutines.cancellation.CancellationException

class FirebaseAuthService : AuthService {

    // The anonymous sign-in is the only step that needs the network, and the identity it
    // returns only labels analytics and crash reports. Being offline means we don't know
    // who the user is — which the contract already allows — never that the app goes down.
    override suspend fun getUserId(): String? {
        val auth = Firebase.auth

        auth.currentUser?.let { return it.uid }

        return try {
            auth.signInAnonymously().user?.uid
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }
}
