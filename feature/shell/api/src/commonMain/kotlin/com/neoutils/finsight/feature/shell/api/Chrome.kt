package com.neoutils.finsight.feature.shell.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import com.neoutils.finsight.ui.component.FloatingActionItem
import org.jetbrains.compose.resources.StringResource

data class ChromeConfig(
    val isBottomBarVisible: Boolean = true,
    val isFloatingActionButtonVisible: Boolean = true,
) {
    companion object {
        val Default = ChromeConfig()
        val ContentOnly = ChromeConfig(
            isBottomBarVisible = false,
            isFloatingActionButtonVisible = false,
        )
    }
}

/**
 * One action a screen offers the app's action button, in the shell's own vocabulary.
 *
 * Concrete counterpart of [FloatingActionItem], declared here for the same reason [NavDestination]
 * is: `:core:designsystem` owns the component and the shape of what it renders, and the shell owns
 * the values the app actually publishes.
 *
 * **The list a screen publishes must be memoized.** [onClick] is compared by reference, so a list
 * rebuilt on every recomposition is never equal to the one before it. `remember(what the action
 * depends on)` is the whole of it — for the categories screen, the filter in force.
 */
data class ChromeAction(
    override val icon: ImageVector,
    override val labelRes: StringResource,
    override val testTag: String,
    override val onClick: () -> Unit,
) : FloatingActionItem

/**
 * What a screen tells the shell about the chrome around it.
 *
 * Everything is keyed by the destination that said it. During a navigation transition two screens
 * are composed at once and both publish, and the one leaving disposes *after* the one entering has
 * already published — a single slot would have the outgoing screen erase the incoming one's word.
 */
interface ChromeController {
    fun publish(destinationId: String, config: ChromeConfig, actions: List<ChromeAction>)
    fun clear(destinationId: String)
}

private object NoOpChromeController : ChromeController {
    override fun publish(
        destinationId: String,
        config: ChromeConfig,
        actions: List<ChromeAction>,
    ) = Unit

    override fun clear(destinationId: String) = Unit
}

val LocalChromeController = staticCompositionLocalOf<ChromeController> {
    NoOpChromeController
}

/**
 * Publishes this screen's chrome — the selector's configuration and the actions its button offers —
 * for as long as the screen is composed.
 *
 * **The screen never names its own identity.** Inside any `composable<Route>{}` the destination's
 * `NavBackStackEntry` is the `ViewModelStoreOwner`, so the identity is read from there: no
 * ceremony per screen, and no call site that can forget it. Composed outside a destination there is
 * nothing to attribute the publication to, and it publishes nothing.
 */
@Composable
fun ChromeEffect(
    config: ChromeConfig = ChromeConfig.Default,
    actions: List<ChromeAction> = emptyList(),
) {
    val controller = LocalChromeController.current
    val destinationId = (LocalViewModelStoreOwner.current as? NavBackStackEntry)?.id ?: return

    SideEffect {
        controller.publish(destinationId, config, actions)
    }

    DisposableEffect(controller, destinationId) {
        onDispose {
            controller.clear(destinationId)
        }
    }
}
