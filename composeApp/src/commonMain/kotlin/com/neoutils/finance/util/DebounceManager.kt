package com.neoutils.finance.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DebounceManager(
    private val delayMillis: Long = 300L,
) {
    private val job = mutableMapOf<String, Job>()

    operator fun invoke(
        scope: CoroutineScope,
        key: String,
        action: suspend () -> Unit
    ) {
        job[key]?.cancel()
        job[key] = scope.launch {
            delay(delayMillis)
            action()
        }
    }
}