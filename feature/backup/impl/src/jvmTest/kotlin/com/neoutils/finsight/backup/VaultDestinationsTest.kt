@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.vault.VaultDestination
import com.neoutils.finsight.domain.vault.VaultDestinations
import com.neoutils.finsight.domain.vault.VaultLocation
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.BackupFolder
import com.neoutils.finsight.ui.screen.backup.service.FolderIdentity
import com.neoutils.finsight.ui.screen.backup.service.FolderLink
import com.neoutils.finsight.ui.screen.backup.service.PRE_MIGRATION_BACKUP_NAME
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.neoutils.finsight.ui.screen.backup.service.UnreachableDestination
import com.neoutils.finsight.ui.screen.backup.service.folderIdentity
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

/**
 * Which rung a copy goes to, which is the one decision that must live in exactly one place.
 *
 * The rungs are recorders here on purpose: what is being asked is not what a folder does
 * but *which* of the two was addressed, and by a preference read at the moment of the call
 * rather than at the moment the object was built. A view model that resolved the rung once
 * would go on writing into the folder somebody has just moved away from.
 */
class VaultDestinationsTest {

    private val settings = MapSettings()

    private val state = BackupVaultRepository(settings)

    private class Recorder(val name: String, val calls: MutableList<String>) : BackupDestination {

        override suspend fun put(
            capturedPath: String,
            name: String,
        ): Either<BackupError, StoredBackup> {
            calls += "${this.name}.put"
            return StoredBackup(name, Instant.fromEpochMilliseconds(0), 0).right()
        }

        override suspend fun list(): Either<BackupError, List<StoredBackup>> {
            calls += "$name.list"
            return emptyList<StoredBackup>().right()
        }

        override suspend fun copyOut(
            backup: StoredBackup,
            destinationPath: String,
        ): Either<BackupError, Boolean> {
            calls += "$name.copyOut"
            return true.right()
        }

        override suspend fun remove(backup: StoredBackup): Either<BackupError, Boolean> {
            calls += "$name.remove"
            return true.right()
        }
    }

    /** A [BackupFolder] whose identity is fixed, for a resolver test that never points at anything. */
    private class FixedFolder(override val identity: FolderIdentity?) : BackupFolder {
        override val isOffered = false
        override suspend fun point(context: PlatformContext) = error("rungFor never points")
        override suspend fun link() = FolderLink.NONE
        override suspend fun displayPath(): String? = null
        override fun forgetPrevious() = Unit
    }

    private val calls = mutableListOf<String>()

    private val destinations = VaultDestinations(
        state = state,
        link = MutableStateFlow(FolderLink.NONE),
        appStorage = Recorder("app", calls),
        folder = Recorder("folder", calls),
    )

    @Test
    fun `every operation goes to the rung the preference names`() = runTest {
        val copy = StoredBackup("finsight-backup.db", Instant.fromEpochMilliseconds(0), 0)

        destinations.put("path", "name")
        destinations.list()
        destinations.copyOut(copy, "out")
        destinations.remove(copy)

        assertEquals(listOf("app.put", "app.list", "app.copyOut", "app.remove"), calls)

        calls.clear()
        state.setDestination(VaultDestination.USER_FOLDER)

        destinations.put("path", "name")
        destinations.list()
        destinations.copyOut(copy, "out")
        destinations.remove(copy)

        // The extra `app.list` is the second half of listing a folder: the copy taken before
        // a migration lives in the app's own storage whatever destination is chosen, and a
        // listing of the folder has to go and get it, or it is written and never seen. Only
        // the listing reaches across — the write goes to the folder, and so do a read and a
        // removal of a copy that is not that one.
        assertEquals(
            listOf("folder.put", "folder.list", "app.list", "folder.copyOut", "folder.remove"),
            calls,
        )
    }

    /**
     * A copy is read and removed on the rung it is actually on, and for one of them that is
     * not the rung in force: the copy taken before a migration is in the app's own storage
     * even while the vault is pointed at a folder. Routing it by the rung in force would
     * answer about a file that is not the one on the screen — a removal reporting *done*
     * over a file still sitting in the app, or refusing over one that is not there.
     */
    @Test
    fun `the copy taken before a migration is read and removed inside the app`() = runTest {
        val fromMigration =
            StoredBackup(PRE_MIGRATION_BACKUP_NAME, Instant.fromEpochMilliseconds(0), 0)

        state.setDestination(VaultDestination.USER_FOLDER)
        calls.clear()

        destinations.copyOut(fromMigration, "out")
        destinations.remove(fromMigration)

        assertEquals(listOf("app.copyOut", "app.remove"), calls)
    }

    /**
     * The rung is read per operation and never held. Somebody points at a folder while a
     * screen is open, and the next listing has to be of the folder — a value resolved when
     * the view model was built would leave copies landing where nobody is looking.
     */
    @Test
    fun `the rung is read again on every call and never held`() = runTest {
        destinations.list()
        state.setDestination(VaultDestination.USER_FOLDER)
        destinations.list()
        state.setDestination(VaultDestination.APP_STORAGE)
        destinations.list()

        // The folder's listing is two calls, not one — see the note above.
        assertEquals(listOf("app.list", "folder.list", "app.list", "app.list"), calls)
    }

    /**
     * [VaultDestinations.rungFor] — task 11.10's own resolver, and the reason it exists at
     * all: a [VaultLocation] names app storage, or a folder by identity, and the folder half
     * has to tell two physical folders apart even though both answer
     * [VaultDestination.USER_FOLDER] the same way.
     */
    @Test
    fun `rungFor resolves a location by identity, current, previous, or neither`() {
        val currentId = folderIdentity("current-token")
        val previousId = folderIdentity("previous-token")
        val strangerId = folderIdentity("a-token-nobody-remembers")

        val appRecorder = Recorder("app", calls)
        val currentRecorder = Recorder("current", calls)
        val previousRecorder = Recorder("previous", calls)

        val router = VaultDestinations(
            state = state,
            link = MutableStateFlow(FolderLink.NONE),
            appStorage = appRecorder,
            folder = currentRecorder,
            folderToken = FixedFolder(currentId),
            previousFolder = previousRecorder,
            previousFolderToken = FixedFolder(previousId),
        )

        assertSame(
            appRecorder,
            router.rungFor(VaultLocation(VaultDestination.APP_STORAGE, null)),
            "app storage did not resolve to itself",
        )
        assertSame(
            currentRecorder,
            router.rungFor(VaultLocation(VaultDestination.USER_FOLDER, currentId)),
            "the folder currently pointed at was not found by its own identity",
        )
        assertSame(
            previousRecorder,
            router.rungFor(VaultLocation(VaultDestination.USER_FOLDER, previousId)),
            "the folder just left was not found by its own identity",
        )
        assertSame(
            UnreachableDestination,
            router.rungFor(VaultLocation(VaultDestination.USER_FOLDER, strangerId)),
            "a folder naming neither remembered token was routed somewhere instead of refused",
        )
    }
}
