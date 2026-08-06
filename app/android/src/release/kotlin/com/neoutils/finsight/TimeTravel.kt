package com.neoutils.finsight

import android.app.Activity

/**
 * The release counterpart of the debug time-travel hook: there is no launch argument to read, and
 * the app's clock is [kotlin.time.Clock.System] and nothing else. `MainActivity` calls the same
 * function in both build types, so the call site states the intent once and the variant decides
 * whether it means anything.
 */
@Suppress("UnusedReceiverParameter")
fun Activity.applyTimeTravel() = Unit
