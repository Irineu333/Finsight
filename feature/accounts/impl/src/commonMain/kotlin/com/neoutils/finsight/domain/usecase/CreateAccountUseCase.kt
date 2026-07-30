@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CreateAccountUseCase(
    private val repository: IAccountRepository,
    /**
     * A new account has to be denominated by someone. Until the form offers the choice, the
     * currency this user reads totals in is the honest answer — and reading it here, rather
     * than letting the model default, is what keeps the decision visible at the one site
     * that will later hand it over to the form.
     */
    private val baseCurrencyRepository: IBaseCurrencyRepository,
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
                    currency = baseCurrencyRepository.current(),
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
