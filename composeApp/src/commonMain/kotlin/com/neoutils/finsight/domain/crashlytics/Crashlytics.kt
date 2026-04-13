package com.neoutils.finsight.domain.crashlytics

interface Crashlytics {
    fun setUserId(id: String?)
    fun recordException(e: Throwable)
}
