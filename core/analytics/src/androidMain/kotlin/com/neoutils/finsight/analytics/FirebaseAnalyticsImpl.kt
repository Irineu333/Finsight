package com.neoutils.finsight.analytics

import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.Event
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.FirebaseAnalyticsEvents
import dev.gitlive.firebase.analytics.FirebaseAnalyticsParam
import dev.gitlive.firebase.analytics.analytics
import dev.gitlive.firebase.analytics.logEvent

class FirebaseAnalyticsImpl : Analytics {

    override fun logScreenView(screenName: String) {
        Firebase.analytics.logEvent(FirebaseAnalyticsEvents.SCREEN_VIEW) {
            param(FirebaseAnalyticsParam.SCREEN_NAME, screenName)
        }
    }

    override fun logEvent(event: Event) {
        Firebase.analytics.logEvent(event.name) {
            event.params.forEach { (key, value) -> param(key, value) }
        }
    }

    override fun setUserId(id: String?) {
        Firebase.analytics.setUserId(id)
    }
}
