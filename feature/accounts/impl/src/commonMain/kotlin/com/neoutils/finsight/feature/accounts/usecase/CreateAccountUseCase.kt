@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.accounts.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import com.neoutils.finsight.feature.accounts.exception.AccountException
import com.neoutils.finsight.core.domain.model.Account
import com.neoutils.finsight.feature.accounts.repository.IAccountRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CreateAccountUseCase(
    private val repository: IAccountRepository,
    private val validateAccountName: ValidateAccountNameUseCase,
    private val setDefaultAccount: SetDefaultAccountUseCase,
) {
    suspend operator fun invoke(
        name: String,
        isDefault: Boolean,
        iconKey: String,
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
                    iconKey = iconKey,
                    isDefault = false,
                    createdAt = Clock.System.now().toEpochMilliseconds()
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
