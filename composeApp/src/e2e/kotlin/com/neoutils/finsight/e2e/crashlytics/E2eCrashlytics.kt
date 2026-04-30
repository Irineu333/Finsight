package com.neoutils.finsight.e2e.crashlytics

import com.neoutils.finsight.domain.crashlytics.Crashlytics

class E2eCrashlytics : Crashlytics {
    override fun setUserId(id: String?) = Unit
    override fun recordException(e: Throwable) = Unit
}
