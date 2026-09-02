package com.neoutils.finsight.ui.screen.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * The spring the journey runs on. `StiffnessMediumLow` is the framework's default for enter and
 * exit transitions and therefore the bar's, and the journey exists because the bar arrived or left.
 * `Animatable`'s own default is `StiffnessMedium`, nearly four times stiffer.
 */
private val FabTravelSpring = spring<Float>(stiffness = Spring.StiffnessMediumLow)

/**
 * What the button does with the place it belongs to.
 *
 * **Three places, six ways between them**, and the six are enumerated nowhere — they fall out of
 * these three answers:
 *
 *     on the bar → the corner   [Travel] — the diagonal, down and to the right
 *     the corner → on the bar   [Travel] — that same line, reversed
 *     either     → hidden       [Hold]  — so the way out is straight down
 *     hidden     → either       [Place] — so the way in is straight up
 *
 * The four vertical ones are vertical because the place does not change under them, and not because
 * a sideways component is suppressed anywhere.
 */
internal enum class FabJourney {

    /** On screen at both ends of the change: the one journey there is. */
    Travel,

    /** Leaving. It keeps the place it stands in, and sinks out of that one. */
    Hold,

    /** Not on screen. It is put where it belongs, and rises into that one. */
    Place,
}

/**
 * [isOnScreen] is the button the chrome is currently drawing, [isWanted] the one it is moving
 * towards. They disagree for as long as an entrance or an exit lasts, which is what separates
 * *leaving* from *gone* — a distinction one boolean cannot make.
 */
internal fun fabJourney(isOnScreen: Boolean, isWanted: Boolean): FabJourney = when {
    !isOnScreen -> FabJourney.Place
    isWanted -> FabJourney.Travel
    else -> FabJourney.Hold
}

/**
 * Where the button stands: `0f` docked into the bar, `1f` in the corner, and every value between a
 * point on the one line joining them. One figure and not two, because the bottom anchor and the
 * horizontal bias cannot arrive separately — which is what makes the return trip the outbound one
 * reversed. [target] is `null` while the shell can name no place at all.
 *
 * **Not a `Transition` value.** `animateFloat` takes a function of *one* state, while [FabJourney.Hold]
 * needs the target to stay the place derived from the state being left; `transitionSpec` sees both
 * ends but chooses only how to travel. `snap()` comes closest and is replaced by a spring on
 * interruption, which is exactly when the button is caught mid-move.
 */
@Composable
internal fun fabPlacement(
    target: Float?,
    isDrawn: Boolean,
    isWanted: Boolean,
): Float {
    // Seeded at the first place the shell can name, so the button's first frame *is* that place.
    val placement = remember(target != null) { Animatable(target ?: 0f) }

    val journey = fabJourney(isOnScreen = isDrawn, isWanted = isWanted)

    LaunchedEffect(placement, target, journey) {
        val destination = target ?: return@LaunchedEffect

        when (journey) {
            FabJourney.Travel -> placement.animateTo(destination, FabTravelSpring)
            FabJourney.Place -> placement.snapTo(destination)
            FabJourney.Hold -> Unit
        }
    }

    // Clamped: a spring is not confined to [0, 1], and `lerp` extrapolates.
    return when (journey) {
        // Read whole in the frame the button appears in — the frame the distance out of that place
        // is measured from too. The spring is snapped to the same figure, so the handover is silent.
        FabJourney.Place -> target ?: placement.value

        FabJourney.Travel, FabJourney.Hold -> placement.value
    }.coerceIn(0f, 1f)
}
