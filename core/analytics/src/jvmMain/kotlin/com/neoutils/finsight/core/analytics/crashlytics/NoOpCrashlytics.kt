package com.neoutils.finsight.core.analytics.crashlytics

import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics

internal class NoOpCrashlytics : Crashlytics {
    override fun setUserId(id: String?) = Unit
    override fun recordException(e: Throwable) = Unit
}
