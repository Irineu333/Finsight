package com.neoutils.finsight.database.repository

import com.neoutils.finsight.domain.repository.IRateSyncStateRepository
import com.neoutils.finsight.domain.repository.RateSyncState
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/**
 * The upkeep state over `multiplatform-settings` — the same mechanism
 * [BaseCurrencyRepository] uses, with keys of its own.
 *
 * **Two values and no third.** When the archive was last brought up to date successfully,
 * and which currencies the source refused to quote. There is no error channel and no
 * transient state: a failed synchronisation writes nothing, and the screen infers it from
 * the instant still being the old one (design D9). That is also why *success* is what is
 * persisted rather than *failure* — it survives a restart, which an in-memory error state
 * would not.
 *
 * Both are exposed as a `StateFlow` so the rates screen observes them without needing an
 * event, and no other surface of the app reads them at all.
 */
class RateSyncStateRepository(
    private val settings: Settings,
) : IRateSyncStateRepository {

    private val state = MutableStateFlow(read())

    override fun observe(): StateFlow<RateSyncState> = state

    override suspend fun record(state: RateSyncState) {
        state.lastSyncedAt
            ?.let { settings.putLong(KEY_LAST_SYNCED_AT, it.toEpochMilliseconds()) }
            ?: settings.remove(KEY_LAST_SYNCED_AT)

        settings.putString(KEY_NOT_COVERED, state.notCoveredCurrencies.sorted().joinToString(SEPARATOR))

        this.state.value = state
    }

    /**
     * A repository that has never synchronised answers *never*, and deliberately not some
     * date: the two lead the user to different things, and only one of them is true.
     */
    private fun read() = RateSyncState(
        lastSyncedAt = settings.getLongOrNull(KEY_LAST_SYNCED_AT)?.let(Instant::fromEpochMilliseconds),
        notCoveredCurrencies = settings.getStringOrNull(KEY_NOT_COVERED)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty(),
    )

    companion object {
        private const val KEY_LAST_SYNCED_AT = "rate_sync_last_synced_at"
        private const val KEY_NOT_COVERED = "rate_sync_not_covered"
        private const val SEPARATOR = ","
    }
}
