package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Account

/**
 * Retires an account that has movement.
 *
 * The ledger cannot lose an account that entries reference — its history would
 * stop balancing — so an account with movement is closed: the rows stay, the real
 * type is preserved, and the account only leaves the active listings. Closing a
 * **permanent** account (ASSET/LIABILITY/EQUITY) with a non-zero balance is
 * **refused** (`AccountError.HAS_BALANCE`): archiving does not invent a write-off
 * to zero it — that would put a movement the user never made into their history —
 * so they resolve it first, by transferring, spending or adjusting. A category is
 * a *temporary* account and archives at any balance: its balance is a period
 * total, not money sitting anywhere. (Decision 8.14 reversed the earlier write-off.)
 *
 * Closing an account with no movement is **allowed**: it violates nothing, it
 * just is not the action a screen would offer — `retireActionOf` sends that case
 * to [DeleteAccountUseCase] instead. The domain refuses what would be *invalid*,
 * not what is merely inappropriate; refusing here would also fail a harmless
 * race, where the last transaction is removed between opening the screen and
 * confirming.
 *
 * This is the single owner of "close an account" for accounts, cards and
 * categories alike — all three are facades over one chart-of-accounts row.
 */
interface ArchiveAccountUseCase {
    suspend operator fun invoke(account: Account): Either<Throwable, Unit>
}
