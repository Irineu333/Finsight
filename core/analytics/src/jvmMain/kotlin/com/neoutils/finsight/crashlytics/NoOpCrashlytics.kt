package com.neoutils.finsight.crashlytics

import com.neoutils.finsight.domain.crashlytics.Crashlytics

internal class NoOpCrashlytics : Crashlytics {
    override fun setUserId(id: String?) = Unit
    override fun recordException(e: Throwable) = Unit
}
