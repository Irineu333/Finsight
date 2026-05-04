@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.recurring.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.feature.recurring.error.RecurringError
import com.neoutils.finsight.feature.recurring.exception.RecurringException
import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.feature.recurring.repository.IRecurringRepository
import com.neoutils.finsight.core.utils.extension.moneyToDouble
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class SaveRecurringUseCase(
    private val repository: IRecurringRepository,
) {
    suspend operator fun invoke(
        id: Long = 0,
        type: Recurring.Type,
        amount: String,
        title: String?,
        dayOfMonth: String,
        category: Category?,
        account: Account?,
        creditCard: CreditCard?,
        createdAt: Long? = null,
        isActive: Boolean = true,
    ): Either<Throwable, Unit> = either {
        ensure(amount.isNotEmpty()) {
            RecurringException(RecurringError.AMOUNT_REQUIRED)
        }

        ensure(amount.moneyToDouble() != 0.0) {
            RecurringException(RecurringError.AMOUNT_ZERO)
        }

        ensure(!title.isNullOrEmpty() || category != null) {
            RecurringException(RecurringError.TITLE_OR_CATEGORY_REQUIRED)
        }

        val day = dayOfMonth.toIntOrNull()

        ensureNotNull(day) {
            RecurringException(RecurringError.INVALID_DAY)
        }

        ensure(day in 1..31) {
            RecurringException(RecurringError.INVALID_DAY)
        }

        if (type.isIncome) {
            ensureNotNull(account) {
                RecurringException(RecurringError.ACCOUNT_REQUIRED)
            }
        } else {
            ensure(account != null || creditCard != null) {
                RecurringException(RecurringError.ACCOUNT_REQUIRED)
            }
        }

        val recurring = Recurring(
            id = id,
            type = type,
            amount = amount.moneyToDouble(),
            title = title,
            dayOfMonth = day,
            categoryId = category?.id,
            accountId = account?.id,
            creditCardId = if (type.isIncome) null else creditCard?.id,
            createdAt = createdAt ?: Clock.System.now().toEpochMilliseconds(),
            isActive = isActive,
        )

        catch {
            if (id == 0L) repository.insert(recurring)
            else repository.update(recurring)
        }.bind()
    }
}
