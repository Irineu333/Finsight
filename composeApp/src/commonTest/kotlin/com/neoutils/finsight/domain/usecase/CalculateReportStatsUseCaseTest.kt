package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CalculateReportStatsUseCaseTest {

    private val useCase = CalculateReportStatsUseCase()

    @Test
    fun accountPerspectiveIncludesAdjustmentsInPeriodAndInitialBalance() {
        val account = Account(id = 1, name = "Conta Principal")
        val otherAccount = Account(id = 2, name = "Conta Secundaria")

        val operations = listOf(
            operation(
                date = LocalDate(2026, 3, 1),
                transactions = listOf(
                    transaction(
                        type = Transaction.Type.INCOME,
                        amount = 100.0,
                        date = LocalDate(2026, 3, 1),
                        account = account,
                    )
                ),
            ),
            operation(
                date = LocalDate(2026, 3, 5),
                transactions = listOf(
                    transaction(
                        type = Transaction.Type.ADJUSTMENT,
                        amount = -30.0,
                        date = LocalDate(2026, 3, 5),
                        account = account,
                    )
                ),
            ),
            operation(
                date = LocalDate(2026, 3, 10),
                transactions = listOf(
                    transaction(
                        type = Transaction.Type.EXPENSE,
                        amount = 40.0,
                        date = LocalDate(2026, 3, 10),
                        account = account,
                    )
                ),
            ),
            operation(
                date = LocalDate(2026, 3, 12),
                transactions = listOf(
                    transaction(
                        type = Transaction.Type.ADJUSTMENT,
                        amount = 25.0,
                        date = LocalDate(2026, 3, 12),
                        account = account,
                    )
                ),
            ),
            operation(
                date = LocalDate(2026, 3, 12),
                transactions = listOf(
                    transaction(
                        type = Transaction.Type.INCOME,
                        amount = 500.0,
                        date = LocalDate(2026, 3, 12),
                        account = otherAccount,
                    )
                ),
            ),
        )

        val result = useCase(
            operations = operations,
            perspective = ReportPerspective.AccountPerspective(accountIds = listOf(account.id)),
            startDate = LocalDate(2026, 3, 10),
            endDate = LocalDate(2026, 3, 31),
        )

        assertEquals(0.0, result.income)
        assertEquals(40.0, result.expense)
        assertEquals(-15.0, result.balance)
        assertEquals(70.0, result.initialBalance)
    }

    @Test
    fun creditCardPerspectiveIncludesAdjustmentsInPeriodAndInitialBalance() {
        val creditCard = CreditCard(
            id = 10,
            name = "Visa",
            limit = 1000.0,
            closingDay = 10,
            dueDay = 20,
        )
        val otherCreditCard = creditCard.copy(id = 11, name = "Master")

        val operations = listOf(
            operation(
                date = LocalDate(2026, 2, 25),
                transactions = listOf(
                    creditCardTransaction(
                        type = Transaction.Type.EXPENSE,
                        amount = 100.0,
                        date = LocalDate(2026, 2, 25),
                        creditCard = creditCard,
                    )
                ),
            ),
            operation(
                date = LocalDate(2026, 2, 28),
                transactions = listOf(
                    creditCardTransaction(
                        type = Transaction.Type.INCOME,
                        amount = 30.0,
                        date = LocalDate(2026, 2, 28),
                        creditCard = creditCard,
                    ),
                    creditCardTransaction(
                        type = Transaction.Type.ADJUSTMENT,
                        amount = -5.0,
                        date = LocalDate(2026, 2, 28),
                        creditCard = creditCard,
                    ),
                ),
            ),
            operation(
                date = LocalDate(2026, 3, 15),
                transactions = listOf(
                    creditCardTransaction(
                        type = Transaction.Type.EXPENSE,
                        amount = 200.0,
                        date = LocalDate(2026, 3, 15),
                        creditCard = creditCard,
                    )
                ),
            ),
            operation(
                date = LocalDate(2026, 3, 16),
                transactions = listOf(
                    creditCardTransaction(
                        type = Transaction.Type.INCOME,
                        amount = 80.0,
                        date = LocalDate(2026, 3, 16),
                        creditCard = creditCard,
                    ),
                    creditCardTransaction(
                        type = Transaction.Type.ADJUSTMENT,
                        amount = 10.0,
                        date = LocalDate(2026, 3, 16),
                        creditCard = creditCard,
                    ),
                ),
            ),
            operation(
                date = LocalDate(2026, 3, 16),
                transactions = listOf(
                    creditCardTransaction(
                        type = Transaction.Type.EXPENSE,
                        amount = 999.0,
                        date = LocalDate(2026, 3, 16),
                        creditCard = otherCreditCard,
                    )
                ),
            ),
        )

        val result = useCase(
            operations = operations,
            perspective = ReportPerspective.CreditCardPerspective(creditCardId = creditCard.id),
            startDate = LocalDate(2026, 3, 1),
            endDate = LocalDate(2026, 3, 31),
        )

        assertEquals(80.0, result.income)
        assertEquals(200.0, result.expense)
        assertEquals(-110.0, result.balance)
        assertEquals(-75.0, result.initialBalance)
    }

    private fun operation(
        date: LocalDate,
        transactions: List<Transaction>,
    ): Operation {
        return Operation(
            kind = Operation.Kind.TRANSACTION,
            title = null,
            date = date,
            transactions = transactions,
        )
    }

    private fun transaction(
        type: Transaction.Type,
        amount: Double,
        date: LocalDate,
        account: Account,
    ): Transaction {
        return Transaction(
            type = type,
            amount = amount,
            title = null,
            date = date,
            target = Transaction.Target.ACCOUNT,
            account = account,
        )
    }

    private fun creditCardTransaction(
        type: Transaction.Type,
        amount: Double,
        date: LocalDate,
        creditCard: CreditCard,
    ): Transaction {
        return Transaction(
            type = type,
            amount = amount,
            title = null,
            date = date,
            target = Transaction.Target.CREDIT_CARD,
            creditCard = creditCard,
        )
    }
}
