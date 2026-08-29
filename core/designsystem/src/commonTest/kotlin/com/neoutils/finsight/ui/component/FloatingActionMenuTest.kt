@file:OptIn(ExperimentalTestApi::class)

package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.accounts_add
import com.neoutils.finsight.resources.accounts_add_transaction
import com.neoutils.finsight.resources.accounts_transfer
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **The form of the button is derived from the list, and the body is not the menu.**
 *
 * Three facts nothing else can hold up. The count decides the shape, so a screen never has a say
 * in it; the body runs the primary action rather than opening the menu, which is what keeps that
 * action at one tap on every screen; and the scrim dismisses without running anything, so a tap
 * meant to close the menu never spends money.
 */
class FloatingActionMenuTest {

    private data class Action(
        override val labelRes: StringResource,
        override val testTag: String,
        override val icon: ImageVector = Icons.Default.Add,
        override val onClick: () -> Unit,
    ) : FloatingActionItem

    @Composable
    private fun Host(
        actions: List<FloatingActionItem>,
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
    ) = Box {
        FloatingActionMenuScrim(
            visible = expanded,
            onDismissRequest = { onExpandedChange(false) },
        )

        FloatingActionMenu(
            actions = actions,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
        )
    }

    @Test
    fun `no action draws no button`() = runComposeUiTest {
        setContent {
            Host(actions = emptyList(), expanded = false, onExpandedChange = {})
        }
        waitForIdle()

        onNodeWithTag(FLOATING_ACTION_EXPAND_TEST_TAG).assertDoesNotExist()
        onNodeWithTag("accounts_add").assertDoesNotExist()
    }

    @Test
    fun `one action draws a button with no expand control, and a tap runs it`() = runComposeUiTest {
        var ran = 0

        setContent {
            Host(
                actions = listOf(
                    Action(Res.string.accounts_add, "accounts_add") { ran++ },
                ),
                expanded = false,
                onExpandedChange = {},
            )
        }
        waitForIdle()

        onNodeWithTag(FLOATING_ACTION_EXPAND_TEST_TAG).assertDoesNotExist()
        onNodeWithTag("accounts_add").performClick()
        waitForIdle()

        assertEquals(1, ran)
    }

    @Test
    fun `three actions draw an expand control, and the body runs the primary without opening`() =
        runComposeUiTest {
            var primary = 0
            var expanded by mutableStateOf(false)

            setContent {
                Host(
                    actions = listOf(
                        Action(Res.string.accounts_add, "accounts_add") { primary++ },
                        Action(Res.string.accounts_transfer, "accounts_add_transfer", Icons.Default.SwapHoriz) {},
                        Action(Res.string.accounts_add_transaction, "accounts_add_transaction") {},
                    ),
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                )
            }
            waitForIdle()

            onNodeWithTag(FLOATING_ACTION_EXPAND_TEST_TAG).assertIsDisplayed()

            onNodeWithTag("accounts_add").performClick()
            waitForIdle()

            assertEquals(1, primary)
            assertEquals(false, expanded)
            onNodeWithTag("accounts_add_transfer").assertDoesNotExist()

            onNodeWithTag(FLOATING_ACTION_EXPAND_TEST_TAG).performClick()
            waitForIdle()

            assertEquals(true, expanded)
            onNodeWithTag("accounts_add_transfer").assertIsDisplayed()
            onNodeWithTag("accounts_add_transaction").assertIsDisplayed()
        }

    @Test
    fun `a tap outside closes the menu and runs nothing`() = runComposeUiTest {
        var ran = 0
        var expanded by mutableStateOf(true)

        setContent {
            Host(
                actions = listOf(
                    Action(Res.string.accounts_add, "accounts_add") { ran++ },
                    Action(Res.string.accounts_transfer, "accounts_add_transfer", Icons.Default.SwapHoriz) { ran++ },
                ),
                expanded = expanded,
                onExpandedChange = { expanded = it },
            )
        }
        waitForIdle()

        onNodeWithTag(FLOATING_ACTION_SCRIM_TEST_TAG).performClick()
        waitForIdle()

        assertEquals(false, expanded)
        assertEquals(0, ran)
    }
}
