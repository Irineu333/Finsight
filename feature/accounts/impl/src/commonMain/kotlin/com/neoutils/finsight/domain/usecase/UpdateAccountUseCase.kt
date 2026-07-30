package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository

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
                // Unconditional, and that is the whole of the rule (design D12): the currency
                // is an attribute of identity, so nothing — not whether the account has
                // entries, not any other state — is consulted before refusing. Keeping it
                // here rather than only in the form is what stops the app from being the only
                // thing that knows.
                ensure(newAccount.currency == oldAccount.currency) {
                    AccountException(AccountError.CURRENCY_IMMUTABLE)
                }

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
