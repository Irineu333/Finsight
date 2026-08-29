package com.neoutils.finsight.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import com.neoutils.finsight.feature.shell.api.ChromeAction
import com.neoutils.finsight.feature.shell.api.ChromeConfig
import com.neoutils.finsight.feature.shell.api.ChromeController

/**
 * A register per destination, and not a slot with a filter.
 *
 * Two screens are composed at once during a navigation transition, and the one leaving goes on
 * recomposing — and publishing — until the animation ends, disposing only afterwards. With a single
 * slot the outgoing screen would overwrite it with its own identity for the whole animation, and
 * the shell, finding nothing for the destination in focus, would draw nothing. Keyed, the outgoing
 * screen's writing never touches the incoming screen's.
 *
 * The actions live beside the configuration rather than inside it: `ChromeConfig` is the target of
 * the shell's `updateTransition`, and a list of lambdas is never equal to itself between
 * recompositions, which would restart that transition on every frame.
 */
internal class ChromeStateHolder : ChromeController {

    private data class Published(
        val config: ChromeConfig,
        val actions: List<ChromeAction>,
    )

    private val registry = mutableStateMapOf<String, Published>()

    fun configOf(destinationId: String?): ChromeConfig =
        registry[destinationId]?.config ?: ChromeConfig.Default

    fun actionsOf(destinationId: String?): List<ChromeAction> =
        registry[destinationId]?.actions.orEmpty()

    override fun publish(
        destinationId: String,
        config: ChromeConfig,
        actions: List<ChromeAction>,
    ) {
        // A screen publishes on every recomposition; writing an identical value would invalidate
        // whoever reads the register and recompose the shell for nothing.
        val published = registry[destinationId]

        if (published?.config == config && published.actions == actions) return

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
