@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.vault.VaultDestination
import com.neoutils.finsight.domain.vault.VaultDestinations
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
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

    private val calls = mutableListOf<String>()

    private val destinations = VaultDestinations(
        state = state,
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

        assertEquals(
            listOf("folder.put", "folder.list", "folder.copyOut", "folder.remove"),
            calls,
        )
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

        assertEquals(listOf("app.list", "folder.list", "app.list"), calls)
    }
}
