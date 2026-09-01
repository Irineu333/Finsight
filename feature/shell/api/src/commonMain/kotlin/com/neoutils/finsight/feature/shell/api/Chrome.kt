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

/**
 * Where a screen will take the app's action button.
 *
 * The button has two homes and the shell picks between them by window width: in a compact window it
 * floats over the bottom-right corner of the content, and from `WIDE` upwards it is the navigation
 * rail's header, beside the content and over nothing. A screen with no room for it usually means
 * only the first — the corner it drew something of its own in — and that is what [BesideContent]
 * says. Which home each width gets is the shell's rule; a screen reading the breakpoint to decide
 * for itself would be a second copy of that rule, in as many screens as ever need it.
 */
enum class ActionButtonPresence {

    /** Wherever the shell puts it. */
    Anywhere,

    /** Only where it stands beside the content: the rail's header, never the floating corner. */
    BesideContent,

    /** Neither place. */
    Nowhere,
}

data class ChromeConfig(
    val isBottomBarVisible: Boolean = true,
    val actionButton: ActionButtonPresence = ActionButtonPresence.Anywhere,
) {
    companion object {
        val Default = ChromeConfig()
        val ContentOnly = ChromeConfig(
            isBottomBarVisible = false,
            actionButton = ActionButtonPresence.Nowhere,
        )

        /**
         * The chrome of a screen that has no room for a button over its content — because it drew
         * its own affordance in that corner, or because the universal action it would be served
         * has nothing to do with what the screen is for.
         */
        val NoButtonOverContent = ChromeConfig(
            actionButton = ActionButtonPresence.BesideContent,
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
    fun publish(destinationId: String, config: ChromeConfig?, actions: List<ChromeAction>)
    fun clear(destinationId: String)
}

private object NoOpChromeController : ChromeController {
    override fun publish(
        destinationId: String,
        config: ChromeConfig?,
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
 *
 * **A null [config] is not the default — it is "no word yet".** A screen whose chrome depends on
 * what it is still reading cannot answer, and the answer it would guess is one it has to take back:
 * the bar and the button move once on the guess and again on the reading, and the button is caught
 * halfway to the place the guess sent it. Saying nothing leaves the chrome on screen exactly as it
 * is until the reading lands, and then everything moves once, together and in the right direction.
 */
@Composable
fun ChromeEffect(
    config: ChromeConfig? = ChromeConfig.Default,
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
