package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Account

/**
 * Retires an account that has movement.
 *
 * The ledger cannot lose an account that entries reference — its history would
 * stop balancing — so an account with movement is closed: the rows stay, the real
 * type is preserved, and the account only leaves the active listings. Closing
 * with a balance writes a dated write-off against reconciliation, so the amount
 * becomes an explicit equity movement instead of quietly leaving net worth.
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
