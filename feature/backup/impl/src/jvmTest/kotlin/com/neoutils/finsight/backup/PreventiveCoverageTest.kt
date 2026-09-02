package com.neoutils.finsight.backup

import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.domain.vault.VaultPreventiveCoverage
import com.neoutils.finsight.feature.backup.api.DestructiveAction
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a destructive confirmation is told about its own action, which is what decides
 * whether its sheet may stop calling the loss permanent.
 *
 * **The answer is asserted against the classification rather than against a list of
 * actions.** A test naming the covered ones would pass just as happily against a screen
 * that carried the same list — and a list in a screen is exactly what design D7 forbids.
 * What is pinned here is that the answer is *derived*: move an action between classes and
 * every sheet changes what it says, with nothing else touched.
 */
class PreventiveCoverageTest {

    private val vault = BackupVaultRepository(MapSettings())
    private val coverage = VaultPreventiveCoverage(vault)

    /** The switch governs this as it governs every trigger (design D1). */
    @Test
    fun `a vault that is off keeps nothing before anything`() {
        DestructiveAction.entries.forEach { action ->
            assertFalse(coverage.keepsCopyBefore(action), "$action, on a vault that is off")
        }
    }

    /**
     * The trigger has a switch of its own, and a sheet that promised a copy while it is off
     * would be promising something the app does not do.
     */
    @Test
    fun `the preventive trigger switched off keeps nothing, though the vault is on`() {
        vault.setOn(true)
        vault.setPreventiveOn(false)

        DestructiveAction.entries.forEach { action ->
            assertFalse(coverage.keepsCopyBefore(action), "$action, with the trigger off")
        }
    }

    /**
     * With both switches on, the whole of what is left is the action's class — every action
     * this app has, answered by the class it was given and by nothing else.
     */
    @Test
    fun `with the vault on, the class of the action is the whole of the answer`() {
        vault.setOn(true)

        DestructiveAction.entries.forEach { action ->
            assertEquals(
                action.classification.isCoveredByPreventiveCapture,
                coverage.keepsCopyBefore(action),
                "$action was answered against something other than its class",
            )
        }
    }

    /**
     * The five confirmations that carry the sentence are told yes, and the deletions the
     * domain already guards are told no. Named here because these are the answers the
     * screens act on, and a class silently emptied would leave the three tests above green.
     */
    @Test
    fun `the five deletion confirmations are told a copy is kept, a guarded facade is not`() {
        vault.setOn(true)

        listOf(
            DestructiveAction.DELETE_TRANSACTION,
            DestructiveAction.DELETE_INSTALLMENT,
            DestructiveAction.DELETE_INVOICE,
            DestructiveAction.DELETE_CURRENCY,
            DestructiveAction.REMOVE_EXCHANGE_RATE,
        ).forEach { action ->
            assertTrue(coverage.keepsCopyBefore(action), "$action carries the sentence")
        }

        assertFalse(coverage.keepsCopyBefore(DestructiveAction.DELETE_CATEGORY))
        assertFalse(coverage.keepsCopyBefore(DestructiveAction.EDIT_TRANSACTION))
    }

    /**
     * The vault is read when the question is asked, so a confirmation built after the
     * switch moved is told what is true now — including the sheet whose own offer turned
     * the vault on a moment ago.
     */
    @Test
    fun `the answer follows the vault rather than the moment this was built`() {
        assertFalse(coverage.keepsCopyBefore(DestructiveAction.DELETE_TRANSACTION))

        vault.setOn(true)

        assertTrue(coverage.keepsCopyBefore(DestructiveAction.DELETE_TRANSACTION))
    }
}
