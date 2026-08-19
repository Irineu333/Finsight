package com.neoutils.finsight.database

/**
 * The most identities a single `IN (…)` may bind.
 *
 * SQLite refuses a statement past a fixed number of host parameters, and Room writes **one
 * parameter per element** of an `IN (:ids)` list without chunking anything. A read whose list
 * the caller did not bound therefore throws on exactly the large history that makes reading in
 * bulk worth doing — the failure lands where the size is, and nowhere smaller ever sees it.
 *
 * Where the ceiling actually is was measured against the driver this project links against,
 * `androidx.sqlite:sqlite-bundled`: 32 766 parameters accepted, 32 767 refused with
 * `too many SQL variables`. This sits some thirty-six times below that, and also below the
 * 999 that SQLite builds older than 3.32 compile in — so the number holds even if the app ever
 * reads through a platform driver instead of the bundled one.
 */
internal const val MAX_BOUND_IDENTITIES = 900

/**
 * [read] over these identities, asked in chunks SQLite will bind, concatenated in order.
 *
 * Every identity is asked exactly once: the chunks partition the list, so nothing is read twice
 * and nothing falls between two of them.
 */
internal suspend fun <T> Collection<Long>.readByIdentity(
    read: suspend (List<Long>) -> List<T>,
): List<T> = chunked(MAX_BOUND_IDENTITIES).flatMap { read(it) }
