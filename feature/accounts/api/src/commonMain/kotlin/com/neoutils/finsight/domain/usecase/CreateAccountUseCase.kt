package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Account

/**
 * Creates one of the user's accounts.
 *
 * **The currency is stated by the caller and has no default**, which is what makes the
 * account form the one door a second currency is born through: there is no expression
 * here that decides one, so nothing can create an account in a currency nobody chose
 * (design D28). It is fixed from this moment and never changes (design D12).
 *
 * It takes no identity: there is nothing to resolve, since the account it operates on
 * is the one it brings into existence.
 */
interface CreateAccountUseCase {
    suspend operator fun invoke(
        name: String,
        isDefault: Boolean,
        iconKey: String,
        currency: String,
        yieldsInterest: Boolean = false,
    ): Either<Throwable, Account>
}
