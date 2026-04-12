package com.neoutils.finsight.crashlytics

import com.neoutils.finsight.domain.crashlytics.Crashlytics
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics

class FirebaseCrashlyticsImpl : Crashlytics {

    override fun setUserId(id: String?) {
        Firebase.crashlytics.setUserId(id ?: "")
    }

    override fun recordException(e: Exception) {
        Firebase.crashlytics.recordException(e)
    }
}
