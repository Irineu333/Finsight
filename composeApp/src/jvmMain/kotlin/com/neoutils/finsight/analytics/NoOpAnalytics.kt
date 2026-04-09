package com.neoutils.finsight.analytics

import com.neoutils.finsight.domain.analytics.Analytics

class NoOpAnalytics : Analytics {
    override fun logScreenView(screenName: String) = Unit
    override fun logEvent(name: String, params: Map<String, String>) = Unit
    override fun setUserId(id: String?) = Unit
}
