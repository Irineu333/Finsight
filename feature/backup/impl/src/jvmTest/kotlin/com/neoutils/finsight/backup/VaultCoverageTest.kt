package com.neoutils.finsight.backup

import com.neoutils.finsight.domain.vault.VaultCoverage
import com.neoutils.finsight.domain.vault.VaultDestination
import com.neoutils.finsight.domain.vault.coverage
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_coverage_app
import com.neoutils.finsight.resources.backup_coverage_app_desktop
import com.neoutils.finsight.resources.backup_coverage_folder
import com.neoutils.finsight.ui.screen.backup.sentence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * What the screen is entitled to say about where the copies survive.
 *
 * There is no Compose harness in this repository, so the sentence is pinned the way the
 * restore confirmation's is ([RestoreClaimsTest]): the decision is a value, and each test
 * names the resource the screen will resolve. A claim about somebody's backups that stopped
 * being true then fails here rather than on their device, at the moment they are deciding
 * whether they are protected.
 *
 * **This suite runs on the JVM, which is the desktop — and that is the point of it.** The
 * app's own storage there is `~/.finance/`, which no uninstall empties (design D3: *no
 * desktop os dois degraus coincidem*, and `JvmBackupDestination`'s own comment). The
 * sentence that used to be said for it claimed the opposite in as many words, on the one
 * screen whose whole reason for existing is not to state protection that is not there — in
 * that case understating it, which is the gentler direction to be wrong in and still wrong.
 * The mobile platforms keep the sentence they always had, and nothing here can say it for
 * them; what it can do is fail the moment somebody makes the desktop share theirs again.
 */
class VaultCoverageTest {

    @Test
    fun `the app's own storage on the desktop is not said to die with the app`() {
        assertEquals(
            VaultCoverage.APP_FOLDER_ON_DESKTOP,
            VaultDestination.APP_STORAGE.coverage,
            "the desktop's own storage outlives the app, and was described as if it did not",
        )

        assertEquals(
            Res.string.backup_coverage_app_desktop,
            VaultDestination.APP_STORAGE.coverage.sentence,
        )
    }

    @Test
    fun `a folder the person pointed at is described as one, whatever the platform`() {
        assertEquals(VaultCoverage.CHOSEN_FOLDER, VaultDestination.USER_FOLDER.coverage)

        assertEquals(
            Res.string.backup_coverage_folder,
            VaultDestination.USER_FOLDER.coverage.sentence,
        )
    }

    /**
     * Three coverages, three sentences — because two of them sharing one would be exactly
     * the bug this exists for: a place described by the sentence belonging to a different
     * place, which reads as a complete statement and is a false one.
     */
    @Test
    fun `no two coverages are said in the same sentence`() {
        val sentences = VaultCoverage.entries.map { it.sentence }

        assertEquals(
            VaultCoverage.entries.size,
            sentences.toSet().size,
            "two rungs share a sentence, so one of them is described as somewhere it is not",
        )

        assertNotEquals(Res.string.backup_coverage_app, Res.string.backup_coverage_app_desktop)
    }
}
