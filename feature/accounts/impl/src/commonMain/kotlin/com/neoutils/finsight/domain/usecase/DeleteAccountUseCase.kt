package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.left
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Account

/**
 * What the user calls "delete an account". Whether the account is removed or
 * closed is not the user's decision but the ledger's: an account with entries
 * cannot be removed without breaking them, so it is closed instead.
 *
 * The result is an `Either` rather than a thrown exception because deleting used
 * to let a raw `SQLiteException` pass straight through `either { }` and out of
 * the ViewModel scope — reaching the user as a crash and crashlytics never.
 */
class DeleteAccountUseCase(
    private val closeAccountUseCase: CloseAccountUseCase,
) {
    suspend operator fun invoke(
        account: Account
    ): Either<Throwable, CloseAccountUseCase.Outcome> {
        if (account.isDefault) {
            return AccountException(AccountError.CANNOT_DELETE_DEFAULT).left()
        }
        return closeAccountUseCase(account)
    }
}
