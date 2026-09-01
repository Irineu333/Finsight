package com.neoutils.finsight.ui.screen.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import com.neoutils.finsight.feature.shell.api.ChromeAction
import com.neoutils.finsight.feature.shell.api.ChromeConfig
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.accounts_add
import com.neoutils.finsight.resources.budgets_create
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **What one destination publishes is not what another reads.**
 *
 * During a navigation transition both screens are composed, and the one leaving keeps publishing
 * until it disposes — after the one entering has already published. A single slot would therefore
 * hand the shell whichever screen wrote last, and the keyed register exists precisely so that it
 * cannot. The third case guards the other half: the actions travel beside the configuration, so
 * changing them leaves the value the chrome's transition animates on untouched.
 */
class ChromeStateHolderTest {

    private val accounts = "destination-accounts"
    private val budgets = "destination-budgets"

    private fun action(
        testTag: String,
        labelRes: StringResource = Res.string.accounts_add,
    ) = ChromeAction(
        icon = Icons.Default.Add,
        labelRes = labelRes,
        testTag = testTag,
        onClick = {},
    )

    @Test
    fun `reading one destination never returns another's actions`() {
        val holder = ChromeStateHolder()

        holder.publish(accounts, ChromeConfig.Default, listOf(action("accounts_add")))
        holder.publish(budgets, ChromeConfig.Default, listOf(action("budgets_add", Res.string.budgets_create)))

        assertEquals(listOf("accounts_add"), holder.actionsOf(accounts).map { it.testTag })
        assertEquals(listOf("budgets_add"), holder.actionsOf(budgets).map { it.testTag })
        assertEquals(emptyList(), holder.actionsOf("destination-nobody"))
    }

    @Test
    fun `the screen that leaves clears only its own register`() {
        val holder = ChromeStateHolder()

        holder.publish(accounts, ChromeConfig.ContentOnly, listOf(action("accounts_add")))
        holder.publish(budgets, ChromeConfig.Default, listOf(action("budgets_add", Res.string.budgets_create)))

        // What a navigation does, in order: the entering screen publishes, then the leaving one
        // disposes. Before this was keyed, that second step erased the first.
        holder.clear(accounts)

        assertEquals(listOf("budgets_add"), holder.actionsOf(budgets).map { it.testTag })
        assertEquals(ChromeConfig.Default, holder.configOf(budgets))
        assertEquals(emptyList(), holder.actionsOf(accounts))
        assertNull(holder.configOf(accounts))
    }

    @Test
    fun `a destination that has published nothing does not answer the default`() {
        val holder = ChromeStateHolder()

        holder.publish(accounts, ChromeConfig.NoButtonOverContent, listOf(action("accounts_add")))

        // The frame between the navigation and the entering screen's `SideEffect`: the shell knows
        // the destination and the destination has said nothing. Answering `Default` here is not
        // silence but a request to show the button, and between two screens that both suppress it
        // — settings and backup — that is the button appearing and leaving again.
        assertNull(holder.configOf(budgets))
        assertEquals(ChromeConfig.NoButtonOverContent, holder.configOf(accounts))
    }

    @Test
    fun `republishing with other actions leaves the configuration alone`() {
        val holder = ChromeStateHolder()

        holder.publish(accounts, ChromeConfig.ContentOnly, listOf(action("accounts_add")))
        val before = holder.configOf(accounts)

        holder.publish(
            accounts,
            ChromeConfig.ContentOnly,
            listOf(action("accounts_add"), action("accounts_add_transfer")),
        )

        assertEquals(before, holder.configOf(accounts))
        assertEquals(2, holder.actionsOf(accounts).size)
    }
}
