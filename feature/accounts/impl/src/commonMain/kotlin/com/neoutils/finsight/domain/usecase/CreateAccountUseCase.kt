@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Creates one of the user's accounts.
 *
 * **The currency is stated by the caller and has no default**, which is what makes the
 * account form the one door a second currency is born through: there is no expression
 * here that decides one, so nothing can create an account in a currency nobody chose
 * (design D28). It is fixed from this moment and never changes (design D12).
 */
class CreateAccountUseCase(
    private val repository: IAccountRepository,
    private val validateAccountName: ValidateAccountNameUseCase,
    private val setDefaultAccount: SetDefaultAccountUseCase,
) {
    suspend operator fun invoke(
        name: String,
        isDefault: Boolean,
        iconKey: String,
        currency: String,
        yieldsInterest: Boolean = false,
    ): Either<Throwable, Account> {
        return either {
            validateAccountName(
                name = name,
            ).mapLeft {
                AccountException(it)
            }.bind()

            val account = catch {
                Account(
                    name = name.trim(),
                    currency = currency,
                    iconKey = iconKey,
                    isDefault = false,
                    createdAt = Clock.System.now().toEpochMilliseconds(),
                    yieldsInterest = yieldsInterest,
                )
            }.bind()

            catch {
                account.copy(
                    id = repository.insert(account)
                )
            }.bind()
        }.onRight { account ->
            if (isDefault) {
                setDefaultAccount(account.id)
            }
        }
    }
}
