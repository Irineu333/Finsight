package com.neoutils.finsight.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.floating_action_collapse
import com.neoutils.finsight.resources.floating_action_expand
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One offer of the app's action button.
 *
 * Same mould as [BottomNavigationItem], for the same reason: the component renders whatever
 * implements this, so it never has to know which screen — or which feature — produced the offer.
 * The label is a [StringResource] rather than a `UiText` because an action is always named by copy
 * the app ships, and because `UiText` is not part of this module's public surface.
 */
interface FloatingActionItem {
    val icon: ImageVector
    val labelRes: StringResource

    /** Locale-independent handle on this action, so an E2E flow never reaches it by its label. */
    val testTag: String

    val onClick: () -> Unit
}

/** Where the menu grows from the button that opens it. The caller anchors it; this animates it. */
enum class FloatingActionMenuDirection {
    /** Upwards, over the content — the button sits at the bottom of the window. */
    Above,

    /** Sideways, over the content — the button is the header of the navigation rail. */
    Beside,
}

/** The expand control of a button that offers more than one action. */
const val FLOATING_ACTION_EXPAND_TEST_TAG = "floating_action_expand"

/** The area an open menu is dismissed on (see [FloatingActionMenuScrim]). */
const val FLOATING_ACTION_SCRIM_TEST_TAG = "floating_action_scrim"

private val ButtonHeight = 56.dp
private val BodyWidth = 56.dp
private val ExpandWidth = 48.dp

/**
 * The action button, in the three forms its list of actions decides.
 *
 * The form is derived, never declared: no action draws nothing, one action draws a plain button
 * that runs it, and two or more draw a body — which runs the **first** action, the primary one —
 * beside an expand control that reveals the rest. The body never opens the menu, so the primary
 * action costs a single tap on every screen.
 *
 * The menu is [FloatingActionMenuList] and the scrim is [FloatingActionMenuScrim]: three
 * composables rather than one because the caller places them at three different points. In a wide
 * window the button is the rail's header, which clips, so the menu has to be drawn outside it; and
 * the scrim covers the whole window, so that the navigation bar stops taking taps while it is up.
 */
@Composable
fun FloatingActionMenuButton(
    actions: List<FloatingActionItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = actions.firstOrNull() ?: return
    val primaryLabel = stringResource(primary.labelRes)

    if (actions.size == 1) {
        FloatingActionButton(
            onClick = primary.onClick,
            modifier = modifier.testTag(primary.testTag),
        ) {
            Icon(
                imageVector = primary.icon,
                contentDescription = primaryLabel,
                modifier = Modifier.size(24.dp),
            )
        }

        return
    }

    val containerColor = FloatingActionButtonDefaults.containerColor
    val contentColor = contentColorFor(containerColor)

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "FloatingActionExpandRotation",
    )

    Surface(
        shape = FloatingActionButtonDefaults.shape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 6.dp,
        modifier = modifier.height(ButtonHeight),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = BodyWidth, height = ButtonHeight)
                    .clickable(
                        role = Role.Button,
                        onClick = primary.onClick,
                    )
                    .testTag(primary.testTag),
            ) {
                Icon(
                    imageVector = primary.icon,
                    contentDescription = primaryLabel,
                    modifier = Modifier.size(24.dp),
                )
            }

            VerticalDivider(
                color = contentColor.copy(alpha = 0.24f),
                modifier = Modifier.height(32.dp),
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = ExpandWidth, height = ButtonHeight)
                    .clickable(
                        role = Role.Button,
                        onClick = { onExpandedChange(!expanded) },
                    )
                    .testTag(FLOATING_ACTION_EXPAND_TEST_TAG),
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandLess,
                    contentDescription = stringResource(
                        if (expanded) {
                            Res.string.floating_action_collapse
                        } else {
                            Res.string.floating_action_expand
                        }
                    ),
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotation),
                )
            }
        }
    }
}

/**
 * The actions the expand control reveals — everything after the primary one.
 *
 * It takes the same list the button does and drops the primary itself, so the two cannot disagree
 * about which action that is. Every item is labelled: an icon alone would name nothing.
 */
@Composable
fun FloatingActionMenuList(
    actions: List<FloatingActionItem>,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    direction: FloatingActionMenuDirection,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
) {
    val secondary = actions.drop(1)

    if (secondary.isEmpty()) return

    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn() + scaleIn(initialScale = 0.8f) + when (direction) {
            FloatingActionMenuDirection.Above -> expandVertically(expandFrom = Alignment.Bottom)
            FloatingActionMenuDirection.Beside -> expandHorizontally(expandFrom = Alignment.Start)
        },
        exit = fadeOut() + scaleOut(targetScale = 0.8f) + when (direction) {
            FloatingActionMenuDirection.Above -> shrinkVertically(shrinkTowards = Alignment.Bottom)
            FloatingActionMenuDirection.Beside -> shrinkHorizontally(shrinkTowards = Alignment.Start)
        },
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Nearest the button first: opening upwards, that is the last row drawn.
            val ordered = when (direction) {
                FloatingActionMenuDirection.Above -> secondary.asReversed()
                FloatingActionMenuDirection.Beside -> secondary
            }

            ordered.forEach { action ->
                MenuItem(
                    action = action,
                    onDismissRequest = onDismissRequest,
                )
            }
        }
    }
}

@Composable
private fun MenuItem(
    action: FloatingActionItem,
    onDismissRequest: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colorScheme.surface,
        contentColor = colorScheme.onSurface,
        shadowElevation = 3.dp,
        modifier = Modifier
            .clickable(
                role = Role.Button,
                onClick = {
                    onDismissRequest()
                    action.onClick()
                },
            )
            .testTag(action.testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = action.icon,
                // The label beside it is the name; a description would read it twice.
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )

            Text(
                text = stringResource(action.labelRes),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * What an open menu is dismissed on.
 *
 * A sibling of the whole window rather than of the button: the point is that nothing behind the
 * menu goes on taking taps, and a scrim confined to the content area would leave the navigation
 * bar clickable. Tapping it closes the menu and runs nothing.
 */
@Composable
fun FloatingActionMenuScrim(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                )
                .testTag(FLOATING_ACTION_SCRIM_TEST_TAG),
        )
    }
}

/**
 * The button with its menu stacked above it — the arrangement of a window whose button sits at the
 * bottom. A wide window places the two apart, and composes [FloatingActionMenuButton] and
 * [FloatingActionMenuList] itself.
 */
@Composable
fun FloatingActionMenu(
    actions: List<FloatingActionItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
) {
    Column(
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        FloatingActionMenuList(
            actions = actions,
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            direction = FloatingActionMenuDirection.Above,
            horizontalAlignment = horizontalAlignment,
        )

        FloatingActionMenuButton(
            actions = actions,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
        )
    }
}
