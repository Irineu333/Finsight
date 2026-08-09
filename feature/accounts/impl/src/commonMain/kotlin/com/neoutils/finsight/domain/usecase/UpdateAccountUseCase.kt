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

/**
 * Updates an account — everything about it except the one attribute that is its
 * identity.
 *
 * **The currency is refused unconditionally** (design D12): not "once the account has
 * entries", but always, and the refusal reads no state at all. A conditional refusal is
 * one somebody has to remember to keep correct, and the condition would be answering a
 * question that does not apply — currency is an attribute of identity, not of history.
 *
 * The rule lives here, in the domain, and not only in the form that hides the control:
 * the project's own layering forbids the inversion where a screen is the only thing
 * keeping an invariant.
 */
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
                ensure(newAccount.currency == oldAccount.currency) {
                    AccountException(AccountError.CURRENCY_IS_IMMUTABLE)
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
