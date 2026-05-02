package com.neoutils.finsight.feature.accounts.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.feature.accounts.error.AccountError
import com.neoutils.finsight.feature.accounts.exception.AccountException
import com.neoutils.finsight.core.domain.model.Account
import com.neoutils.finsight.feature.accounts.repository.IAccountRepository

class UpdateAccountUseCase(
    private val repository: IAccountRepository,
    private val validateAccountName: ValidateAccountNameUseCase,
    private val setDefaultAccount: SetDefaultAccountUseCase,
) {
    suspend operator fun invoke(
        accountId: Long,
        update: (Account) -> Account,
    ): Either<Throwable, Account> {
        return either {
            val oldAccount = catch {
                ensureNotNull(
                    repository.getAccountById(accountId)
                ) {
                    AccountException(AccountError.NOT_FOUND)
                }
            }.bind()

            catch {
                update(oldAccount)
            }.onRight { newAccount ->
                validateAccountName(
                    name = newAccount.name,
                    ignoreId = accountId,
                ).mapLeft {
                    AccountException(it)
                }.bind()

                catch {
                    repository.update(newAccount)
                }.bind()
            }.bind()
        }.onRight {
            if (it.isDefault) {
                setDefaultAccount(it.id)
            }
        }
    }
}
