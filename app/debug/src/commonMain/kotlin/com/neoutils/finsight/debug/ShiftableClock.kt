package com.neoutils.finsight.debug

import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A clock that can be moved: [Clock.System] plus a shift, which is zero until a test asks
 * otherwise.
 *
 * It exists because a whole class of behaviour — an invoice closing, a recurring transaction
 * falling due — is only reachable once time has passed, and the device's own clock cannot be moved
 * on a production build (`SET_TIME` is a signature permission). The app reads the time through an
 * injected [Clock]; this is the implementation a debug build binds in its place, which is what
 * lets a test say "forty-five days later" without asking anything of the operating system.
 *
 * The shift is relative, never an absolute date: what a test needs is *later than the state it
 * just created*, and only a relative move keeps that relation whatever day the suite runs on.
 *
 * It lives in this module, and not in the one that declares the binding, so that a release build
 * does not contain a movable clock at all — not merely a movable clock that nothing happens to
 * move. `:core:common` binds `Clock.System` and knows nothing of this. Both apps keep it out the
 * same way: Android by depending on `:app:debug` from `debugImplementation` alone, iOS by
 * compiling the source set that names it only when Xcode's Debug configuration asks Gradle for it.
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
