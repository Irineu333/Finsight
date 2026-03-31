package com.neoutils.finsight.extension

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Intercepts long press at [PointerEventPass.Initial] so it fires even on components that have
 * their own tap/click handlers. Without this, child clickable handlers running on
 * [PointerEventPass.Main] compete with the outer detector and win, silently swallowing the
 * gesture before the long press threshold is reached. Once the long press wins, it consumes the
 * remaining pointer events until release so the inner tap action cannot complete on pointer up.
 */
internal fun Modifier.interceptLongPress(onLongPress: () -> Unit): Modifier = pointerInput(onLongPress) {
    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
        var released = false
        var canceled = false
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: run {
                    canceled = true
                    break
                }

                if (!change.pressed) {
                    released = true
                    break
                }

                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    canceled = true
                    break
                }

                val finalEvent = awaitPointerEvent(pass = PointerEventPass.Final)
                val finalChange = finalEvent.changes.firstOrNull { it.id == down.id } ?: run {
                    canceled = true
                    break
                }

                if (finalChange.isConsumed) {
                    canceled = true
                    break
                }
            }
        }
        if (released || canceled) {
            return@awaitEachGesture
        }

        onLongPress()

        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
        }
    }
}
