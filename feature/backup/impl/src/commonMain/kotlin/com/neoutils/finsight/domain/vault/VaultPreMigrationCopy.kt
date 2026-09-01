package com.neoutils.finsight.domain.vault

import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.database.snapshot.PreMigrationCopyTarget
import com.neoutils.finsight.database.snapshot.isMigrationPending

/**
 * The third trigger, and the only one that answers a question instead of asking for a copy:
 * whoever assembles the database wants a path, and this is where the vault's switch decides
 * whether there is one.
 *
 * **This is the one place the switch governs this capture** (design D11). `:core:database`
 * takes a path and captures, or takes none and does not — it consults no preference, has no
 * file API and does not know a vault exists — so the whole of design D1 for this trigger is
 * the `null` below. The three triggers are then one rule read in one place each, and none of
 * them can be on while the vault is off.
 *
 * **A pending migration is asked about before anything is written, and that is not a
 * duplicate of the capture's own check.** The copy lives under a single reserved name, so a
 * new one eventually replaces the one that is there — and the copy from the last migration
 * is precisely what must survive every ordinary opening and all of retention (design D10),
 * because the damage it exists to undo is found out days later. Asking first is what keeps
 * an opening that migrates nothing from disturbing it at all; the question itself has one
 * owner, in `:core:database`, and is not re-derived here from a schema version.
 *
 * The name it ends up under is not decoration either: retention recognises the copy it must
 * not sweep by that name, so a path ending in anything else would be counted with the
 * periodic copies and carried off as soon as they exceeded the limit in force.
 *
 * **The copy in force is replaced and never cleared to make room.** A new one is written
 * beside it and only takes its place once it has been read as a database of a schema this
 * build would migrate — which is the same question [isPending] answers about the archive,
 * asked of the file that was just written. It refuses everything the failure it exists for
 * produces: nothing at all where the `VACUUM` was refused and swallowed, half a file where
 * the process was killed, and the well-formed empty database a full volume leaves behind —
 * the last being the one that would otherwise sit under the reserved name for good, listed
 * as a plausible copy, never swept, and offered to somebody as a way back.
 */
class VaultPreMigrationCopy(
    private val state: BackupVaultRepository,
    private val place: MigrationCopyPlace,
    private val isPending: (String) -> Boolean = ::isMigrationPending,
) : PreMigrationCopyTarget {

    /** Where the last answer pointed, so that [settle] knows what to look at. */
    private var staged: String? = null

    override fun path(): String? {
        if (!state.observe().value.isOn) return null
        if (!isPending(place.archivePath)) return null

        return place.stagedCopyPath().also { staged = it }
    }

    override fun settle() {
        val written = staged ?: return
        staged = null
        place.settleStagedCopy(keep = isPending(written))
    }
}
