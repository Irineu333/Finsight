package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Account

/**
 * Retires an account from the chart of accounts, whatever facade it wears.
 *
 * The ledger cannot lose an account that entries reference — its history would
 * stop balancing — so an account with movement is **closed**, never deleted: the
 * rows stay, the real type is preserved, and the account only leaves the active
 * listings. An account that never moved has nothing to preserve and is removed.
 *
 * Closing with money still in it writes a dated balanced write-off against the
 * reconciliation account, so the amount becomes an explicit equity movement
 * instead of quietly disappearing from net worth.
 *
 * This is the single owner of "retire an account" for accounts, cards and
 * categories alike — the three used to do it three different ways, and two of
 * them left a ledger account with no facade.
 */
interface CloseAccountUseCase {
    suspend operator fun invoke(account: Account): Either<Throwable, Outcome>

    /**
     * What [invoke] would do, without doing it — so a screen can name the action
     * ("excluir" vs "encerrar") by asking the rule instead of re-deriving it.
     */
    suspend fun outcomeFor(account: Account): Outcome

    enum class Outcome {
        /** Never moved: removed outright. */
        DELETED,

        /** Had movement: closed, history intact. */
        CLOSED,

        /** Had movement and a balance: closed, with a write-off to zero it. */
        CLOSED_WITH_WRITE_OFF,
    }
}
