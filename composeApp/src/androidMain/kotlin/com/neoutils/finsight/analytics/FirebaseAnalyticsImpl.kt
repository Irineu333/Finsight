package com.neoutils.finsight.analytics

import com.neoutils.finsight.domain.analytics.Analytics
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import dev.gitlive.firebase.analytics.logEvent

class FirebaseAnalyticsImpl : Analytics {

    override fun logScreenView(screenName: String) {
        Firebase.analytics.logEvent("screen_view") {
            param("screen_name", screenName)
        }
    }

    override fun logEvent(name: String, params: Map<String, String>) {
        Firebase.analytics.logEvent(name) {
            params.forEach { (key, value) -> param(key, value) }
        }
    }

    override fun setUserId(id: String?) {
        Firebase.analytics.setUserId(id)
    }
}
