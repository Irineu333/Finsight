package com.neoutils.finsight.report

import androidx.activity.ComponentActivity
import java.lang.ref.WeakReference

class ActivityHolder {
    private var reference: WeakReference<ComponentActivity>? = null

    val activity: ComponentActivity? get() = reference?.get()

    fun set(activity: ComponentActivity) {
        reference = WeakReference(activity)
    }

    fun clear() {
        reference = null
    }
}
