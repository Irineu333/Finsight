package com.neoutils.finsight

import com.neoutils.finsight.debug.debugModule as sharedDebugModule

/**
 * What a debug build adds to the app's graph, which is `:app:debug` and nothing of Android's own.
 *
 * `AndroidApp` aggregates this in both build types; the variant decides whether there is anything
 * in it, and the release counterpart is an empty module.
 */
val debugModule = sharedDebugModule
