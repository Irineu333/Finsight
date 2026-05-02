package com.neoutils.finsight.core.analytics.crashlytics

import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics

internal class FirebaseCrashlyticsImpl : Crashlytics {

    override fun setUserId(id: String?) {
        Firebase.crashlytics.setUserId(id ?: "")
    }

    override fun recordException(e: Throwable) {
        Firebase.crashlytics.recordException(e)
    }
}
