package com.neoutils.finsight.core.analytics

import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.core.analytics.Event

internal class NoOpAnalytics : Analytics {
    override fun logScreenView(screenName: String) = Unit
    override fun logEvent(event: Event) = Unit
    override fun setUserId(id: String?) = Unit
}
