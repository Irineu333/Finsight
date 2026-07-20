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
 * Refuses an account with no movement: closing exists *because* deletion is
 * impossible, so an account that never moved has nothing to preserve and would
 * only be hidden beyond reach. [DeleteAccountUseCase] is the action for that one.
 *
 * This is the single owner of "close an account" for accounts, cards and
 * categories alike — all three are facades over one chart-of-accounts row.
 */
interface CloseAccountUseCase {
    suspend operator fun invoke(account: Account): Either<Throwable, Unit>
}
