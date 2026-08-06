@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.util

import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The app's clock: [Clock.System] plus a shift that is zero everywhere but in a test run.
 *
 * It exists because a whole class of behaviour — an invoice closing, a recurring transaction
 * falling due — is only reachable once time has passed, and the device's own clock cannot be moved
 * on a production build (`SET_TIME` is a signature permission). Reading the time through an
 * injected [Clock] is what lets a test say "forty-five days later" without asking anything of the
 * operating system.
 *
 * The shift is applied, never the absolute date: what a test needs is *later than the state it
 * just created*, and only a relative move keeps that relation whatever day the suite runs on.
 *
 * Who may move it is answered outside this module, and by a build type rather than by a platform:
 * the reader of the launch argument lives in `:app:android`'s debug source set. Only Android has
 * one today, because that is where the E2E suite runs — the other targets simply never move it,
 * and would need a channel of their own before they could. Left alone, this reads exactly as
 * `Clock.System` does.
 */
class ShiftableClock(
    private val source: Clock = Clock.System,
) : Clock {

    @Volatile
    private var shift: Duration = Duration.ZERO

    override fun now(): Instant = source.now() + shift

    /**
     * Sets the distance from the real now — it does not accumulate. `shiftTo(Duration.ZERO)` is
     * how a caller returns to the present, which is what a relaunch carrying no offset means.
     */
    fun shiftTo(offset: Duration) {
        shift = offset
    }
}
