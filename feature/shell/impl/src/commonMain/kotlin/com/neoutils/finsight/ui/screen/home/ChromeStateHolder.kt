package com.neoutils.finsight.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import com.neoutils.finsight.feature.shell.api.ChromeAction
import com.neoutils.finsight.feature.shell.api.ChromeConfig
import com.neoutils.finsight.feature.shell.api.ChromeController

/**
 * A register per destination, and not a slot.
 *
 * The screen being left goes on publishing until its animation ends, disposing only afterwards, so
 * a single slot would carry its identity for the whole transition and the shell would find nothing
 * for the destination in focus.
 *
 * The actions live beside the configuration rather than inside it: `ChromeConfig` is the target of
 * the shell's `updateTransition`, and a list of lambdas is never equal to itself between
 * recompositions.
 */
internal class ChromeStateHolder : ChromeController {

    private data class Published(
        val config: ChromeConfig?,
        val actions: List<ChromeAction>,
    )

    private val registry = mutableStateMapOf<String, Published>()

    /**
     * What this destination published, or `null` while it has said nothing — not composed yet, or
     * composed with nothing to say (`ChromeEffect(config = null)`). Both silences are one.
     *
     * Silence is not `ChromeConfig.Default`. A screen publishes from a `SideEffect`, which runs
     * after the composition that read this, so the frame in which the shell learns it has navigated
     * is one in which the destination has said nothing — and `Default` there is a request to show
     * the button, which between two screens that both suppress it is the button appearing and going.
     */
    fun configOf(destinationId: String?): ChromeConfig? =
        registry[destinationId]?.config

    fun actionsOf(destinationId: String?): List<ChromeAction> =
        registry[destinationId]?.actions.orEmpty()

    /**
     * Whether no destination at all has registered — nothing on screen for the shell to hold on to.
     *
     * Silence is bounded by this. A destination that has not spoken *yet* shares the register with
     * the screen being left, which is still composed; one that never speaks is alone in an empty
     * register, and holding a chrome computed for somebody else would then be permanent.
     */
    val isSilent: Boolean get() = registry.isEmpty()

    override fun publish(
        destinationId: String,
        config: ChromeConfig?,
        actions: List<ChromeAction>,
    ) {
        // A screen publishes on every recomposition; writing an identical value would invalidate
        // whoever reads the register and recompose the shell for nothing.
        val published = registry[destinationId]

        if (published != null && published.config == config && published.actions == actions) return

        registry[destinationId] = Published(config, actions)
    }

    override fun clear(destinationId: String) {
        registry.remove(destinationId)
    }
}

@Composable
internal fun rememberChromeStateHolder(): ChromeStateHolder {
    return remember { ChromeStateHolder() }
}
