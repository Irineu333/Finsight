package com.neoutils.finsight.domain.analytics

interface Analytics {
    fun logScreenView(screenName: String)
    fun logEvent(event: Event)
    fun setUserId(id: String?)
}
