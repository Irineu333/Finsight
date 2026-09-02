@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup

import com.neoutils.finsight.database.snapshot.ArchiveCounts
import com.neoutils.finsight.domain.model.BackupPlatform
import com.neoutils.finsight.domain.restore.FileOrigin
import com.neoutils.finsight.domain.restore.RestoreConfirmation
import com.neoutils.finsight.domain.restore.RestoreSource
import com.neoutils.finsight.domain.vault.VaultState
import com.neoutils.finsight.feature.backup.api.DestructiveAction
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_confirm_age
import com.neoutils.finsight.resources.backup_confirm_file_age
import com.neoutils.finsight.resources.backup_confirm_irreversible
import com.neoutils.finsight.resources.backup_confirm_irreversible_title
import com.neoutils.finsight.resources.backup_confirm_reversible
import com.neoutils.finsight.resources.backup_confirm_reversible_title
import com.neoutils.finsight.ui.modal.confirmRestore.RestoreAftermath
import com.neoutils.finsight.ui.modal.confirmRestore.ageSentence
import com.neoutils.finsight.ui.modal.confirmRestore.restoreAftermath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * What the restore confirmation is entitled to say, in every state it can be put in.
 *
 * There is no Compose test harness in this repository, so the sheet cannot be rendered and
 * read back. What can be pinned is the decision behind it, and that is why the decision is a
 * value: each test names the resource the sheet will resolve, so a sentence that stopped
 * being true in a state fails here rather than on somebody's phone at the moment their
 * archive is replaced.
 *
 * Two claims are under test and they are independent.
 *
 * **How far back the file reaches** is a different sentence per source, and the difference is
 * not a nicety. A kept copy is this install's own archive at the instant stamped in it, so
 * restoring it really does take the app back to how it was. A file from the picker carries
 * the same four columns whoever wrote it — the backup screen's own tile says it normally
 * comes from *another device* — so "how it was" would name a state this app was never in.
 *
 * **What is left afterwards** is the vault's answer for this action and not a reading of its
 * switches, which is what keeps the sheet and the trigger from coming apart (design D7).
 */
class RestoreClaimsTest {

    private val stamped = FileOrigin(
        platform = BackupPlatform.ANDROID,
        platformId = BackupPlatform.ANDROID.id,
        appVersion = "2.4.1",
        createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
    )

    private fun confirmation(source: RestoreSource, origin: FileOrigin? = stamped) =
        RestoreConfirmation(
            origin = origin,
            counts = ArchiveCounts(accounts = 6, transactions = 1_482, categories = 24, creditCards = 2),
            source = source,
        )

    private fun vault(on: Boolean = true, preventive: Boolean = true) =
        VaultState(isOn = on, isPreventiveOn = preventive)

    // ------------------------------------------------------------------ how far back

    @Test
    fun `a kept copy is the one file restoring can be said to take the app back to`() {
        assertEquals(
            Res.string.backup_confirm_age,
            ageSentence(confirmation(RestoreSource.KEPT_COPY)),
            "a copy the vault took is this archive at that instant",
        )
    }

    @Test
    fun `a picked file is only said to have been written, never to be this app's past`() {
        val sentence = ageSentence(confirmation(RestoreSource.PICKED_FILE))

        assertEquals(Res.string.backup_confirm_file_age, sentence)
        assertTrue(
            sentence != Res.string.backup_confirm_age,
            "nothing in a picked file says which device wrote it, so how this app used to be " +
                "is not a fact the sheet has",
        )
    }

    @Test
    fun `a file that carries no stamp is dated by nothing at all`() {
        for (source in RestoreSource.entries) {
            assertNull(
                ageSentence(confirmation(source, origin = null)),
                "$source, with no stamp to read a span from",
            )
        }
    }

    // ------------------------------------------------------------------ what is left after

    @Test
    fun `both switches on is the one state that promises a copy`() {
        val aftermath = restoreAftermath(
            vault().keepsCopyBefore(DestructiveAction.RESTORE_BACKUP)
        )

        assertEquals(RestoreAftermath.COPY_KEPT, aftermath)
        assertEquals(Res.string.backup_confirm_reversible_title, aftermath.title)
        assertEquals(Res.string.backup_confirm_reversible, aftermath.message)
    }

    @Test
    fun `the preventive trigger off promises nothing, on a vault that is otherwise on`() {
        val aftermath = restoreAftermath(
            vault(preventive = false).keepsCopyBefore(DestructiveAction.RESTORE_BACKUP)
        )

        assertEquals(
            RestoreAftermath.NO_COPY,
            aftermath,
            "the trigger that would write the copy is switched off",
        )
        assertEquals(Res.string.backup_confirm_irreversible_title, aftermath.title)
        assertEquals(Res.string.backup_confirm_irreversible, aftermath.message)
    }

    @Test
    fun `a vault that is off promises nothing, whatever its triggers say`() {
        for (preventive in listOf(true, false)) {
            assertEquals(
                RestoreAftermath.NO_COPY,
                restoreAftermath(
                    vault(on = false, preventive = preventive)
                        .keepsCopyBefore(DestructiveAction.RESTORE_BACKUP)
                ),
                "vault off, preventive $preventive",
            )
        }
    }

    /**
     * The third link of the chain, asserted rather than assumed.
     *
     * The two switches are only two thirds of whether a copy is kept: the action has to be in
     * a class the preventive trigger covers, and that is
     * [com.neoutils.finsight.feature.backup.api.DestructiveClass]'s to decide. Restoring
     * moved out of a covered class would make the sheet's promise false with nothing in the
     * sheet touched, which is exactly the kind of thing a screen carrying half the rule
     * cannot notice.
     */
    @Test
    fun `restoring is in a class the preventive trigger covers`() {
        assertTrue(
            DestructiveAction.RESTORE_BACKUP.classification.isCoveredByPreventiveCapture,
            "the promise the sheet makes rests on this being true",
        )
    }
}
