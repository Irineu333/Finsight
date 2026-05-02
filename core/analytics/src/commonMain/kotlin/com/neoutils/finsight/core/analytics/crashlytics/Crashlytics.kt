package com.neoutils.finsight.core.analytics.crashlytics

interface Crashlytics {
    fun setUserId(id: String?)
    fun recordException(e: Throwable)
}
