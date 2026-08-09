package com.neoutils.finsight.database.repository

import com.neoutils.finsight.domain.repository.IRateSyncStateRepository
import com.neoutils.finsight.domain.repository.RatePair
import com.neoutils.finsight.domain.repository.RateSyncState
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/**
 * The upkeep state over `multiplatform-settings` — the same mechanism
 * [BaseCurrencyRepository] uses, with keys of its own.
 *
 * **Two values and no third.** When each currency was last answered about successfully,
 * and which ones the source refused to quote. There is no error channel and no transient
 * state: a failed quotation writes nothing for that currency, and the screen infers it
 * from the instant still being the old one (design D9). That is also why *success* is what
 * is persisted rather than *failure* — it survives a restart, which an in-memory error
 * state would not.
 *
 * Both are exposed as a `StateFlow` so the rates screen observes them without needing an
 * event, and no other surface of the app reads them at all.
 *
 * The instants are stored as one `FROM>TO=millis` entry per pair, joined — a shape rather
 * than a schema, because `multiplatform-settings` holds scalars and this is a preference,
 * not a table. An entry that cannot be read is dropped: the worst it costs is asking that
 * pair once more.
 */
class RateSyncStateRepository(
    private val settings: Settings,
) : IRateSyncStateRepository {

    private val state = MutableStateFlow(read())

    override fun observe(): StateFlow<RateSyncState> = state

    override suspend fun record(state: RateSyncState) {
        settings.putString(
            KEY_SYNCED_AT,
            state.syncedAt.entries
                .sortedBy { "${it.key.currency}$PAIR${it.key.against}" }
                .joinToString(SEPARATOR) {
                    "${it.key.currency}$PAIR${it.key.against}$ASSIGN${it.value.toEpochMilliseconds()}"
                },
        )
        settings.putString(KEY_NOT_COVERED, state.notCoveredCurrencies.sorted().joinToString(SEPARATOR))

        this.state.value = state
    }

    /**
     * A repository that has never synchronised answers *never*, and deliberately not some
     * date: the two lead the user to different things, and only one of them is true.
     */
    private fun read() = RateSyncState(
        syncedAt = settings.getStringOrNull(KEY_SYNCED_AT)
            ?.split(SEPARATOR)
            ?.mapNotNull { entry ->
                val key = entry.substringBefore(ASSIGN, missingDelimiterValue = "")
                val millis = entry.substringAfter(ASSIGN, missingDelimiterValue = "").toLongOrNull()
                val currency = key.substringBefore(PAIR, missingDelimiterValue = "")
                val against = key.substringAfter(PAIR, missingDelimiterValue = "")
                if (currency.isBlank() || against.isBlank() || millis == null) null
                else RatePair(currency, against) to Instant.fromEpochMilliseconds(millis)
            }
            ?.toMap()
            .orEmpty(),
        notCoveredCurrencies = settings.getStringOrNull(KEY_NOT_COVERED)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty(),
    )

    companion object {
        private const val KEY_SYNCED_AT = "rate_sync_synced_at"
        private const val KEY_NOT_COVERED = "rate_sync_not_covered"
        private const val SEPARATOR = ","
        private const val ASSIGN = "="
        private const val PAIR = ">"
    }
}
