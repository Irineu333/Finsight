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
 * It has two homes, and which width gets which is the shell's to know: the bottom-right corner of a
 * compact window, over the content, and the navigation rail's header from `WIDE` up, beside it. A
 * screen with no room usually means only the first, which is [BesideContent].
 */
enum class ActionButtonPresence {

    /** Wherever the shell puts it. */
    Anywhere,

    /** Only where it stands beside the content: the rail's header, never the floating corner. */
    BesideContent,

    /** Neither place. */
    Nowhere;

    /**
     * Whether the button is drawn over the content — the compact window's corner. Which values
     * answer yes is this enum's to know, so a fourth one is not a hunt through the shell.
     */
    val isOverContent: Boolean get() = this == Anywhere

    /** Whether the button is drawn where it stands **beside** the content — the rail's header. */
    val isBesideContent: Boolean get() = this != Nowhere
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
         * For a screen with no room for a button over its content: it drew its own affordance in
         * that corner, or the universal action it would be served is not what the screen is for.
         */
        val NoButtonOverContent = ChromeConfig(
            actionButton = ActionButtonPresence.BesideContent,
        )
    }
}

/**
 * One action a screen offers the app's action button. Concrete counterpart of [FloatingActionItem],
 * declared here for the same reason [NavDestination] is: `:core:designsystem` owns the shape,
 * the shell owns the values.
 *
 * **The list a screen publishes must be memoized.** [onClick] is compared by reference, so a list
 * rebuilt on every recomposition is never equal to the one before it.
 */
data class ChromeAction(
    override val icon: ImageVector,
    override val labelRes: StringResource,
    override val testTag: String,
    override val onClick: () -> Unit,
) : FloatingActionItem

/**
 * What a screen tells the shell about the chrome around it, keyed by the destination that said it:
 * during a navigation two screens publish at once, and the one leaving disposes *after* the one
 * entering has spoken.
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
 * Publishes this screen's chrome for as long as the screen is composed.
 *
 * The screen never names its own identity: inside any `composable<Route>{}` the destination's
 * `NavBackStackEntry` is the `ViewModelStoreOwner`, so no call site can forget it. Composed outside
 * a destination there is nothing to attribute the publication to, and it publishes nothing.
 *
 * **A null [config] is not the default — it is "no word yet".** A screen whose chrome depends on
 * what it is still reading would otherwise guess, and the guess is one the reading takes back: the
 * chrome moves once on it and again on the answer. Silent, it moves once, when the answer lands.
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
