@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.database.repository

import com.neoutils.finsight.domain.vault.ArchiveCopy
import com.neoutils.finsight.domain.vault.BackupRetention
import com.neoutils.finsight.domain.vault.DEFAULT_INTERVAL
import com.neoutils.finsight.domain.vault.VaultDestination
import com.neoutils.finsight.domain.vault.VaultState
import com.russhwolf.settings.Settings
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the vault is, over `multiplatform-settings` — the same mechanism
 * `RateSyncStateRepository` keeps the upkeep state in, with keys of its own.
 *
 * It is a preference and not a table, and that is the point rather than an economy. The
 * backup file contains the whole archive, so anything the vault knew about itself from
 * inside the database would travel in every copy and come back in time with a restore:
 * a person restoring last week's copy would be told their last backup was last week's
 * (design D9, and the same argument that keeps the history out of the database). Settings
 * belong to the install, which is what every value here is a fact about.
 *
 * The whole state is read once and written whole, and observed as one [StateFlow]. The
 * rules the vault has are about combinations of these values — the limit in force needs
 * the destination and the retention together — so a screen and a trigger reading the same
 * snapshot cannot disagree about what the vault is.
 *
 * A value that cannot be read falls back to what a fresh install would have, and never to
 * something more permissive: an unreadable switch reads as off.
 */
class BackupVaultRepository(
    private val settings: Settings,
) {

    private val state = MutableStateFlow(read())

    fun observe(): StateFlow<VaultState> = state

    /**
     * Turns the whole vault on or off. Every trigger it governs comes into force, or stops,
     * with this one value (design D1).
     */
    fun setOn(isOn: Boolean) = update { it.copy(isOn = isOn) }

    /**
     * Turns the trigger that fires on opening on or off, on its own — the vault itself, and
     * the copy taken before a migration with it, are untouched.
     */
    fun setPeriodicOn(isOn: Boolean) = update { it.copy(isPeriodicOn = isOn) }

    /**
     * Turns the trigger that fires before a destructive action on or off, on its own — the
     * vault itself, and the other two triggers with it, are untouched.
     */
    fun setPreventiveOn(isOn: Boolean) = update { it.copy(isPreventiveOn = isOn) }

    /**
     * Records that the offer beside a destructive action was left unticked and the action
     * went ahead — so the next one arrives unticked and worded as a reminder rather than as
     * a fresh proposal. The offer itself keeps being made for as long as the vault is off.
     */
    fun markDeclined() = update { it.copy(wasDeclined = true) }

    fun setInterval(interval: Duration) = update { it.copy(interval = interval) }

    fun setRetention(retention: BackupRetention) = update { it.copy(retention = retention) }

    fun setDestination(destination: VaultDestination) =
        update { it.copy(destination = destination) }

    /**
     * Records a capture that landed: when it happened, how far the archive had got, and
     * which file it went into.
     *
     * Only success is written, which is the same choice `RateSyncStateRepository` makes and
     * for the same reason: it survives a restart, and a failure is read off the instant
     * still being the old one. A [mark] of null is honest about a reading that did not
     * happen and makes the next capture unconditional.
     *
     * [copy] is the copy the destination actually wrote, never the name it was asked for: a
     * provider is free to hand back one of its own, and what is recorded has to be what a
     * later listing will answer with.
     */
    fun recordCapture(at: Instant, mark: Long?, copy: ArchiveCopy) =
        update { it.copy(lastCapturedAt = at, markAtLastCapture = mark, archiveCopy = copy) }

    /**
     * Forgets both of the things a copy could have been to the archive — that one covers it,
     * and that it came from one — while keeping the fact that a capture happened and when.
     *
     * Those two are separate facts about separate copies, and they are dropped together for
     * one moment only: the archive is about to be replaced, and until the replacement has
     * landed neither is known. The instant is untouched, because it has not stopped being
     * true — a copy was taken then, it is still the last one that succeeded, and that is the
     * line the screen shows.
     *
     * Clearing before rather than after is what keeps the screen from ever naming a copy the
     * archive did not come from: a replacement that fails leaves an unmarked list, which is
     * *unknown* rather than wrong, and the next capture says where the archive is again.
     */
    fun forgetCoverage() = update { it.copy(markAtLastCapture = null, archiveCopy = null) }

    /**
     * Records which kept copy the archive has just been replaced with the content of, or
     * null when it came from a file the user picked and no kept copy describes it.
     *
     * It is written after the replacement has landed and never before — see
     * [forgetCoverage]. Coverage is deliberately not restored here: a restored archive must
     * still be captured, which is the defect design D8's coverage rule exists to prevent.
     */
    fun recordArchiveCopy(copy: ArchiveCopy?) = update { it.copy(archiveCopy = copy) }

    private fun update(transform: (VaultState) -> VaultState) {
        val next = transform(state.value)
        write(next)
        state.value = next
    }

    private fun write(next: VaultState) {
        settings.putBoolean(KEY_ON, next.isOn)
        settings.putBoolean(KEY_PERIODIC_ON, next.isPeriodicOn)
        settings.putBoolean(KEY_PREVENTIVE_ON, next.isPreventiveOn)
        settings.putBoolean(KEY_DECLINED, next.wasDeclined)
        settings.putLong(KEY_INTERVAL_SECONDS, next.interval.inWholeSeconds)
        settings.putString(KEY_RETENTION, next.retention.name)
        settings.putString(KEY_DESTINATION, next.destination.name)
        settings.putLongOrRemove(KEY_CAPTURED_AT, next.lastCapturedAt?.toEpochMilliseconds())
        settings.putLongOrRemove(KEY_CAPTURED_MARK, next.markAtLastCapture)
        settings.putStringOrRemove(KEY_ARCHIVE_COPY_NAME, next.archiveCopy?.name)
        settings.putLongOrRemove(
            KEY_ARCHIVE_COPY_SAVED_AT,
            next.archiveCopy?.savedAt?.toEpochMilliseconds(),
        )
    }

    private fun read() = VaultState(
        isOn = settings.getBoolean(KEY_ON, defaultValue = false),
        // True where the switch above is false, and for the same reason: what a fresh
        // install has. This one is in force only while the vault is on, so defaulting it
        // to on grants nothing — it is what makes turning the vault on enough.
        isPeriodicOn = settings.getBoolean(KEY_PERIODIC_ON, defaultValue = true),
        isPreventiveOn = settings.getBoolean(KEY_PREVENTIVE_ON, defaultValue = true),
        interval = settings.getLongOrNull(KEY_INTERVAL_SECONDS)?.seconds ?: DEFAULT_INTERVAL,
        retention = settings.enumOrNull(KEY_RETENTION, BackupRetention.entries)
            ?: BackupRetention.TEN,
        destination = settings.enumOrNull(KEY_DESTINATION, VaultDestination.entries)
            ?: VaultDestination.APP_STORAGE,
        lastCapturedAt = settings.getLongOrNull(KEY_CAPTURED_AT)
            ?.let(Instant::fromEpochMilliseconds),
        markAtLastCapture = settings.getLongOrNull(KEY_CAPTURED_MARK),
        // Both halves or neither: a name with no instant beside it could not be matched
        // against a listing without guessing, and guessing is what would let it drift.
        archiveCopy = settings.getStringOrNull(KEY_ARCHIVE_COPY_NAME)?.let { name ->
            settings.getLongOrNull(KEY_ARCHIVE_COPY_SAVED_AT)?.let { savedAt ->
                ArchiveCopy(name = name, savedAt = Instant.fromEpochMilliseconds(savedAt))
            }
        },
        // False where every other switch is true: nothing having been turned down is what
        // a fresh install has, and an unreadable value offers rather than reminds.
        wasDeclined = settings.getBoolean(KEY_DECLINED, defaultValue = false),
    )

    private companion object {
        const val KEY_ON = "backup_vault_on"
        const val KEY_PERIODIC_ON = "backup_vault_periodic_on"
        const val KEY_PREVENTIVE_ON = "backup_vault_preventive_on"
        // The stored name is older than what the value means, and is kept because the two
        // agree on every install that has one: the offer had been put and the vault was
        // still off, which is a refusal.
        const val KEY_DECLINED = "backup_vault_offered"
        const val KEY_INTERVAL_SECONDS = "backup_vault_interval_seconds"
        const val KEY_RETENTION = "backup_vault_retention"
        const val KEY_DESTINATION = "backup_vault_destination"
        const val KEY_CAPTURED_AT = "backup_vault_captured_at"
        const val KEY_CAPTURED_MARK = "backup_vault_captured_mark"
        const val KEY_ARCHIVE_COPY_NAME = "backup_vault_archive_copy_name"
        const val KEY_ARCHIVE_COPY_SAVED_AT = "backup_vault_archive_copy_saved_at"
    }
}

/**
 * Null is absence, and absence is a key that is not there — never a sentinel number, which
 * "never captured" would have to be told apart from.
 */
private fun Settings.putLongOrRemove(key: String, value: Long?) {
    if (value == null) remove(key) else putLong(key, value)
}

/** The same, for a value that is text: absence is the key not being there. */
private fun Settings.putStringOrRemove(key: String, value: String?) {
    if (value == null) remove(key) else putString(key, value)
}

/**
 * The stored name, or null when nothing was stored and when what was stored is a name this
 * build no longer has. An option removed from an enum is then read as the default rather
 * than as a crash, which is what a preference file surviving an update has to allow for.
 */
private fun <T : Enum<T>> Settings.enumOrNull(key: String, options: List<T>): T? =
    getStringOrNull(key)?.let { stored -> options.firstOrNull { it.name == stored } }
