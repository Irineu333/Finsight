package com.neoutils.finsight.domain.analytics

interface Analytics {
    fun logScreenView(screenName: String)
    fun logEvent(name: String, params: Map<String, String> = emptyMap())
    fun setUserId(id: String?)
}
