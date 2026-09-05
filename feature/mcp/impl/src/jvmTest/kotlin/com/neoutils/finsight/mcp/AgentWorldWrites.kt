@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.neoutils.finsight.mcp

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.error.BudgetError
import com.neoutils.finsight.domain.error.CategoryError
import com.neoutils.finsight.domain.error.CreditCardError
import com.neoutils.finsight.domain.error.InstallmentError
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.error.RecurringError
import com.neoutils.finsight.domain.error.RecurringRetireError
import com.neoutils.finsight.domain.error.RetireError
import com.neoutils.finsight.domain.error.TransactionError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.exception.BudgetException
import com.neoutils.finsight.domain.exception.CategoryException
import com.neoutils.finsight.domain.exception.CreditCardException
import com.neoutils.finsight.domain.exception.InstallmentException
import com.neoutils.finsight.domain.exception.RecurringException
import com.neoutils.finsight.domain.exception.RecurringRetireException
import com.neoutils.finsight.domain.exception.RetireException
import com.neoutils.finsight.domain.exception.TransactionException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionRegistration
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.form.CreditCardForm
import com.neoutils.finsight.domain.model.form.RecurringForm
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.model.invoiceWindowFor
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.AddCreditCardUseCase
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCase
import com.neoutils.finsight.domain.usecase.BuildTransactionUseCase
import com.neoutils.finsight.domain.usecase.CreateAccountUseCase
import com.neoutils.finsight.domain.usecase.CreateBudgetUseCase
import com.neoutils.finsight.domain.usecase.CreateCategoryUseCase
import com.neoutils.finsight.domain.usecase.CreateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.DeleteAccountUseCase
import com.neoutils.finsight.domain.usecase.DeleteBudgetUseCase
import com.neoutils.finsight.domain.usecase.DeleteCategoryUseCase
import com.neoutils.finsight.domain.usecase.DeleteCreditCardUseCase
import com.neoutils.finsight.domain.usecase.DeleteFutureInvoiceUseCase
import com.neoutils.finsight.domain.usecase.DeleteInstallmentUseCase
import com.neoutils.finsight.domain.usecase.DeleteRecurringUseCase
import com.neoutils.finsight.domain.usecase.DeleteTransactionUseCase
import com.neoutils.finsight.domain.usecase.RegisterTransactionUseCase
import com.neoutils.finsight.domain.usecase.SaveRecurringUseCase
import com.neoutils.finsight.domain.usecase.UpdateAccountUseCase
import com.neoutils.finsight.domain.usecase.UpdateBudgetUseCase
import com.neoutils.finsight.domain.usecase.UpdateCategoryUseCase
import com.neoutils.finsight.domain.usecase.UpdateCreditCardUseCase
import com.neoutils.finsight.domain.usecase.UpdateInstallmentUseCase
import com.neoutils.finsight.domain.usecase.UpdateTransactionUseCase
import com.neoutils.finsight.extension.contraLegFor
import com.neoutils.finsight.extension.editObstacle
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth
import kotlin.math.round
import kotlin.time.Clock

/**
 * The **write** use cases the registration family calls, rebuilt over the real ledger.
 *
 * Their implementations live in each feature's `impl`, which an `impl` may not depend on — the same
 * boundary that put the read stand-ins in `AgentWorldRepositories`. What is faked is therefore the
 * *implementation* and never the ledger: every posting these write goes through the production
 * `TransactionRepository` and its `LedgerEntryWriter`, so `Σ = 0`, the dimension landing rule and
 * the closure guards are the app's own and refuse here exactly as they refuse in the app.
 *
 * Each one keeps the refusal **vocabulary** of the operation it stands for — the same typed errors,
 * so a refusal an assertion reads is the one the app would produce — and each keeps the shape of the
 * rule it owns. What they do not reproduce is the details a feature's own suite already covers;
 * where a rule is the point of a test in this module, it is written out here in full and said so.
 */

// ----------------------------------------------------------------------------------
// Transactions
// ----------------------------------------------------------------------------------

/** Turns a filled form into the intent the ledger writes, resolving the invoice a card leg lands on. */
internal class WorldBuildTransaction(
    private val clock: Clock,
    private val invoiceRepository: IInvoiceRepository,
    private val createInvoice: CreateInvoiceUseCase,
) : BuildTransactionUseCase {

    override suspend fun invoke(form: TransactionForm): Either<Throwable, TransactionIntent> = either {
        ensure(form.amount.moneyToDouble() > 0.0) {
            IllegalArgumentException("The amount must be greater than zero.")
        }
        ensure(!form.title.isNullOrEmpty() || form.category != null) {
            IllegalArgumentException("Enter a title or pick a category.")
        }

        val date = ensureNotNull(runCatching { dayMonthYear.parse(form.date) }.getOrNull()) {
            IllegalArgumentException("That is not a date.")
        }
        ensure(date <= clock.today()) { IllegalArgumentException("The date cannot be in the future.") }

        if (form.target.isAccount) {
            val account = ensureNotNull(form.account) {
                IllegalArgumentException("Pick the account.")
            }
            return@either TransactionIntent(
                title = form.title,
                date = date,
                legs = listOf(TransactionLeg(form.type, form.amount.moneyToDouble(), account.id)),
                contra = contraLegFor(form.type, form.category),
            )
        }

        ensure(form.type == TransactionType.EXPENSE) {
            IllegalArgumentException("Only expenses can go on a credit card.")
        }
        val card = ensureNotNull(form.creditCard) { IllegalArgumentException("Pick the credit card.") }
        val dueMonth = ensureNotNull(form.invoiceDueMonth) { IllegalArgumentException("Pick the invoice.") }
        val invoice = invoiceRepository.invoiceFor(card, dueMonth, createInvoice).bind()

        TransactionIntent(
            title = form.title,
            date = date,
            legs = listOf(
                TransactionLeg(
                    type = form.type,
                    amount = form.amount.moneyToDouble(),
                    accountId = card.accountId,
                    dimensionId = invoice.dimensionId,
                ),
            ),
            contra = contraLegFor(form.type, form.category),
        )
    }
}

/** [months] months after this one — the step an instalment takes from one invoice to the next. */
private fun YearMonth.after(months: Int): YearMonth =
    (0 until months).fold(this) { month, _ -> month.plusMonth() }

/** The cycle a card leg lands on: the one already declared, or one opened on demand. */
private suspend fun IInvoiceRepository.invoiceFor(
    card: CreditCard,
    dueMonth: YearMonth,
    createInvoice: CreateInvoiceUseCase,
): Either<Throwable, Invoice> {
    getInvoicesByCreditCard(card.id).firstOrNull { it.dueMonth == dueMonth }
        ?.let { return Either.Right(it) }
    return createInvoice(card.id, dueMonth)
}

/**
 * The dispatch `RegisterTransactionUseCaseImpl` owns, written out here because it is exactly what
 * `create_transaction` must not do for itself: the split wins over the mark, and a form with one
 * instalment and no mark is a plain posting.
 */
internal class WorldRegisterTransaction(
    private val transactionRepository: ITransactionRepository,
    private val buildTransaction: BuildTransactionUseCase,
    private val addInstallment: AddInstallmentUseCase,
    private val recurringRepository: IRecurringRepository,
    private val clock: Clock,
) : RegisterTransactionUseCase {

    override suspend fun invoke(
        form: TransactionForm,
        isRecurring: Boolean,
    ): Either<Throwable, TransactionRegistration> = either {
        if (form.installments > 1) {
            return@either TransactionRegistration.Installments(
                addInstallment(form, form.installments).bind(),
            )
        }

        val intent = buildTransaction(form).bind()

        TransactionRegistration.Single(
            if (isRecurring) {
                val template = form.asRecurringOn(intent.date)
                    .toRecurring(createdAt = clock.now().toEpochMilliseconds())
                    .mapLeft(::RecurringException)
                    .bind()
                val id = catch { recurringRepository.insert(template) }.bind()
                catch {
                    transactionRepository.createTransaction(
                        intent.copy(recurringId = id, recurringCycle = 1),
                    )
                }.bind()
            } else {
                catch { transactionRepository.createTransaction(intent) }.bind()
            },
        )
    }
}

/**
 * The edit, refusing exactly what the production use case refuses — through the **same**
 * `Transaction.editObstacle`, which is the single owner of what the rewrite can express.
 */
internal class WorldUpdateTransaction(
    private val transactionRepository: ITransactionRepository,
    private val buildTransaction: BuildTransactionUseCase,
) : UpdateTransactionUseCase {

    override suspend fun invoke(
        transactionId: Long,
        form: TransactionForm,
    ): Either<Throwable, Transaction> = either {
        val stored = ensureNotNull(transactionRepository.getTransactionById(transactionId)) {
            TransactionException(TransactionError.NOT_FOUND)
        }

        stored.editObstacle?.let { raise(TransactionException(it)) }

        val intent = buildTransaction(form).bind()

        catch {
            transactionRepository.updateTransaction(
                id = transactionId,
                title = intent.title,
                date = intent.date,
                legs = intent.legs,
                contra = intent.contra,
            )
        }.bind()

        ensureNotNull(transactionRepository.getTransactionById(transactionId)) {
            TransactionException(TransactionError.NOT_FOUND)
        }
    }
}

internal class WorldDeleteTransaction(
    private val transactionRepository: ITransactionRepository,
) : DeleteTransactionUseCase {
    override suspend fun invoke(
        transactionId: Long,
        withoutCopy: Boolean,
    ): Either<Throwable, Unit> = either {
        ensureNotNull(transactionRepository.getTransactionById(transactionId)) {
            TransactionException(TransactionError.NOT_FOUND)
        }
        catch { transactionRepository.deleteTransactionById(transactionId) }.bind()
    }
}

// ----------------------------------------------------------------------------------
// Accounts
// ----------------------------------------------------------------------------------

internal class WorldCreateAccount(
    private val accountRepository: IAccountRepository,
) : CreateAccountUseCase {
    override suspend fun invoke(
        name: String,
        isDefault: Boolean,
        iconKey: String,
        currency: String,
        yieldsInterest: Boolean,
    ): Either<Throwable, Account> = either {
        ensure(name.isNotBlank()) { AccountException(AccountError.EMPTY_NAME) }
        ensure(accountRepository.getAllAccountsIncludingClosed().none { it.name.equals(name.trim(), true) }) {
            AccountException(AccountError.ALREADY_EXIST)
        }
        val account = Account(
            name = name.trim(),
            currency = currency,
            iconKey = iconKey,
            isDefault = isDefault,
            yieldsInterest = yieldsInterest,
        )
        catch { account.copy(id = accountRepository.insert(account)) }.bind()
    }
}

internal class WorldUpdateAccount(
    private val accountRepository: IAccountRepository,
) : UpdateAccountUseCase {
    override suspend fun invoke(
        accountId: Long,
        update: (Account) -> Account,
    ): Either<Throwable, Account> = either {
        val old = ensureNotNull(accountRepository.getAccountById(accountId)) {
            AccountException(AccountError.NOT_FOUND)
        }
        val new = update(old)
        ensure(new.currency == old.currency) { AccountException(AccountError.CURRENCY_IS_IMMUTABLE) }
        catch { accountRepository.update(new) }.bind()
        new
    }
}

internal class WorldDeleteAccount(
    private val accountRepository: IAccountRepository,
    private val entryRepository: IEntryRepository,
    private val recurringRepository: IRecurringRepository,
) : DeleteAccountUseCase {
    override suspend fun invoke(accountId: Long): Either<Throwable, Unit> = either {
        val account = ensureNotNull(accountRepository.getAccountById(accountId)) {
            AccountException(AccountError.NOT_FOUND)
        }
        ensure(!account.isDefault) { AccountException(AccountError.CANNOT_DELETE_DEFAULT) }
        ensure(!entryRepository.hasEntries(account.id)) {
            AccountException(AccountError.HAS_TRANSACTIONS)
        }
        ensure(!recurringRepository.hasRecurringForAccount(account.id)) {
            AccountException(AccountError.HAS_RECURRING)
        }
        catch { accountRepository.delete(account) }.bind()
    }
}

// ----------------------------------------------------------------------------------
// Categories
// ----------------------------------------------------------------------------------

internal class WorldCreateCategory(
    private val categoryRepository: ICategoryRepository,
) : CreateCategoryUseCase {
    override suspend fun invoke(
        name: String,
        iconKey: String,
        type: Category.Type,
    ): Either<Throwable, Category> = either {
        ensure(name.isNotBlank()) { CategoryException(CategoryError.EMPTY_NAME) }
        ensure(!categoryRepository.existsByName(name.trim(), ignoreId = 0)) {
            CategoryException(CategoryError.ALREADY_EXIST)
        }
        val category = Category(
            name = name.trim(),
            icon = com.neoutils.finsight.ui.icons.CategoryLazyIcon(iconKey),
            type = type,
            createdAt = 0,
        )
        catch { categoryRepository.insert(category) }.bind()
        categoryRepository.getAllCategoriesIncludingClosed().last { it.name == name.trim() }
    }
}

internal class WorldUpdateCategory(
    private val categoryRepository: ICategoryRepository,
) : UpdateCategoryUseCase {
    override suspend fun invoke(
        categoryId: Long,
        name: String,
        iconKey: String,
    ): Either<Throwable, Unit> = either {
        val stored = ensureNotNull(categoryRepository.getCategoryById(categoryId)) {
            CategoryException(CategoryError.NOT_FOUND)
        }
        ensure(name.isNotBlank()) { CategoryException(CategoryError.EMPTY_NAME) }
        ensure(!categoryRepository.existsByName(name.trim(), ignoreId = categoryId)) {
            CategoryException(CategoryError.ALREADY_EXIST)
        }
        catch {
            categoryRepository.update(
                stored.copy(name = name.trim(), icon = com.neoutils.finsight.ui.icons.CategoryLazyIcon(iconKey)),
            )
        }.bind()
    }
}

/**
 * Removal, with the four guards `ResolveCategoryRetirabilityUseCase` owns.
 *
 * Written out because it is the point of a test in this module: a category with postings must be
 * refused, and the refusal must be the one that names archiving. The movement guard reads the
 * **real ledger** through the category's own dimension, which is what makes the refusal a fact
 * about the ledger rather than about a flag a fixture set.
 */
internal class WorldDeleteCategory(
    private val categoryRepository: ICategoryRepository,
    private val entryRepository: IEntryRepository,
    private val budgetRepository: IBudgetRepository,
    private val recurringRepository: IRecurringRepository,
    private val accountRepository: IAccountRepository,
) : DeleteCategoryUseCase {
    override suspend fun invoke(categoryId: Long): Either<Throwable, Unit> = either {
        val category = ensureNotNull(categoryRepository.getCategoryById(categoryId)) {
            CategoryException(CategoryError.NOT_FOUND)
        }
        ensure(!entryRepository.hasEntriesForDimension(category.dimensionId)) {
            RetireException(RetireError.HAS_TRANSACTIONS)
        }
        ensure(!budgetRepository.hasBudgetForCategory(categoryId)) {
            RetireException(RetireError.HAS_BUDGET)
        }
        ensure(!recurringRepository.hasRecurringForCategory(categoryId)) {
            RetireException(RetireError.HAS_RECURRING)
        }
        ensure(category.systemKey == null || !accountRepository.hasYieldingAccount()) {
            RetireException(RetireError.HAS_YIELDING_ACCOUNTS)
        }
        catch { categoryRepository.delete(category) }.bind()
    }
}

// ----------------------------------------------------------------------------------
// Cards
// ----------------------------------------------------------------------------------

internal class WorldAddCreditCard(
    private val creditCardRepository: ICreditCardRepository,
    private val createInvoice: CreateInvoiceUseCase,
    private val invoiceRepository: IInvoiceRepository,
    private val clock: Clock,
) : AddCreditCardUseCase {
    override suspend fun invoke(
        form: CreditCardForm,
        currency: String,
    ): Either<Throwable, CreditCard> = either {
        ensure(creditCardRepository.getAllCreditCardsIncludingClosed().none { it.name.equals(form.name.trim(), true) }) {
            CreditCardException(CreditCardError.ALREADY_EXIST_NAME)
        }
        val card = form.build().bind()
        val stored = catch { card.copy(id = creditCardRepository.insert(card, currency)) }.bind()
        // The first cycle opens with the card, as the production use case does: a card whose
        // invoice never opened would take no expense at all.
        val window = stored.invoiceWindowFor(clock.today().yearMonth.plusMonth())
        catch {
            invoiceRepository.insert(
                Invoice(
                    creditCard = stored,
                    openingMonth = window.openingMonth,
                    closingMonth = window.closingMonth,
                    dueMonth = clock.today().yearMonth.plusMonth(),
                    status = Invoice.Status.OPEN,
                ),
            )
        }.bind()
        creditCardRepository.getCreditCardById(stored.id) ?: stored
    }
}

internal class WorldUpdateCreditCard(
    private val creditCardRepository: ICreditCardRepository,
) : UpdateCreditCardUseCase {
    override suspend fun invoke(
        creditCardId: Long,
        block: (CreditCard) -> CreditCard,
    ): Either<Throwable, CreditCard> = either {
        val old = ensureNotNull(creditCardRepository.getCreditCardById(creditCardId)) {
            CreditCardException(CreditCardError.NOT_FOUND)
        }
        val new = block(old)
        ensure(new.name.isNotBlank()) { CreditCardException(CreditCardError.EMPTY_NAME) }
        catch { creditCardRepository.update(new) }.bind()
        creditCardRepository.getCreditCardById(creditCardId) ?: new
    }
}

internal class WorldDeleteCreditCard(
    private val creditCardRepository: ICreditCardRepository,
    private val entryRepository: IEntryRepository,
    private val recurringRepository: IRecurringRepository,
) : DeleteCreditCardUseCase {
    override suspend fun invoke(creditCardId: Long): Either<Throwable, Unit> = either {
        val card = ensureNotNull(creditCardRepository.getCreditCardById(creditCardId)) {
            CreditCardException(CreditCardError.NOT_FOUND)
        }
        ensure(!entryRepository.hasEntries(card.accountId)) {
            AccountException(AccountError.HAS_TRANSACTIONS)
        }
        ensure(!recurringRepository.hasRecurringForCreditCard(card.id)) {
            AccountException(AccountError.HAS_RECURRING)
        }
        catch { creditCardRepository.delete(card) }.bind()
    }
}

// ----------------------------------------------------------------------------------
// Budgets
// ----------------------------------------------------------------------------------

internal class WorldCreateBudget(
    private val budgetRepository: IBudgetRepository,
) : CreateBudgetUseCase {
    override suspend fun invoke(
        title: String,
        categories: List<Category>,
        iconKey: String,
        currency: String,
        limitType: LimitType,
        amount: Double,
        percentage: Double?,
        baseIncome: Recurring?,
    ): Either<Throwable, Budget> = either {
        ensure(title.isNotBlank()) { BudgetException(BudgetError.EMPTY_TITLE) }
        ensure(budgetRepository.getAllBudgets().none { it.title.equals(title.trim(), true) }) {
            BudgetException(BudgetError.ALREADY_EXIST)
        }
        if (limitType == LimitType.PERCENTAGE) {
            ensureNotNull(baseIncome) { BudgetException(BudgetError.MISSING_BASE_INCOME) }
        }
        val limit = if (limitType == LimitType.FIXED) {
            amount
        } else {
            (baseIncome?.amount ?: 0.0) * (percentage ?: 0.0) / 100
        }
        ensure(limit >= 0) { BudgetException(BudgetError.NEGATIVE_LIMIT) }
        val budget = Budget(
            title = title.trim(),
            categories = categories,
            iconKey = iconKey,
            amount = limit,
            currency = currency,
            limitType = limitType,
            percentage = percentage.takeIf { limitType == LimitType.PERCENTAGE },
            recurringId = baseIncome?.id.takeIf { limitType == LimitType.PERCENTAGE },
            createdAt = 0,
        )
        catch { budget.copy(id = budgetRepository.insert(budget)) }.bind()
    }
}

internal class WorldUpdateBudget(
    private val budgetRepository: IBudgetRepository,
) : UpdateBudgetUseCase {
    override suspend fun invoke(
        budgetId: Long,
        title: String,
        categories: List<Category>,
        iconKey: String,
        limitType: LimitType,
        amount: Double,
        percentage: Double?,
        baseIncome: Recurring?,
    ): Either<Throwable, Unit> = either {
        val budget = ensureNotNull(budgetRepository.getBudgetById(budgetId)) {
            BudgetException(BudgetError.NOT_FOUND)
        }
        ensure(title.isNotBlank()) { BudgetException(BudgetError.EMPTY_TITLE) }
        ensure(amount >= 0) { BudgetException(BudgetError.NEGATIVE_LIMIT) }
        catch {
            budgetRepository.update(
                budget.copy(
                    title = title.trim(),
                    categories = categories,
                    iconKey = iconKey,
                    amount = amount,
                    limitType = limitType,
                    percentage = percentage,
                ),
            )
        }.bind()
    }
}

internal class WorldDeleteBudget(
    private val budgetRepository: IBudgetRepository,
) : DeleteBudgetUseCase {
    override suspend fun invoke(budgetId: Long): Either<Throwable, Unit> = either {
        val budget = ensureNotNull(budgetRepository.getBudgetById(budgetId)) {
            BudgetException(BudgetError.NOT_FOUND)
        }
        catch { budgetRepository.delete(budget) }.bind()
    }
}

// ----------------------------------------------------------------------------------
// Recurring
// ----------------------------------------------------------------------------------

internal class WorldSaveRecurring(
    private val recurringRepository: IRecurringRepository,
) : SaveRecurringUseCase {
    override suspend fun invoke(
        id: Long,
        type: TransactionType,
        amount: String,
        title: String?,
        dayOfMonth: String,
        category: Category?,
        account: Account?,
        creditCard: CreditCard?,
        createdAt: Long?,
        isArchived: Boolean,
    ): Either<Throwable, Recurring> = either {
        if (id != 0L) {
            ensureNotNull(recurringRepository.getRecurringById(id)) {
                RecurringException(RecurringError.NOT_FOUND)
            }
        }

        val recurring = RecurringForm(
            type = type,
            amount = amount,
            title = title.orEmpty(),
            dayOfMonth = dayOfMonth,
            account = account,
            creditCard = creditCard,
            category = category,
        ).toRecurring(createdAt = createdAt ?: 0L)
            .mapLeft(::RecurringException)
            .bind()
            .copy(id = id, isArchived = isArchived)

        catch {
            if (id == 0L) {
                recurring.copy(id = recurringRepository.insert(recurring))
            } else {
                recurringRepository.update(recurring)
                recurring
            }
        }.bind()
    }
}

internal class WorldDeleteRecurring(
    private val recurringRepository: IRecurringRepository,
    private val budgetRepository: IBudgetRepository,
) : DeleteRecurringUseCase {
    override suspend fun invoke(recurringId: Long): Either<Throwable, Unit> = either {
        val recurring = ensureNotNull(recurringRepository.getRecurringById(recurringId)) {
            RecurringException(RecurringError.NOT_FOUND)
        }
        ensure(!recurringRepository.hasTransactionForRecurring(recurringId)) {
            RecurringRetireException(RecurringRetireError.HAS_TRANSACTIONS)
        }
        ensure(!budgetRepository.hasBudgetForRecurring(recurringId)) {
            RecurringRetireException(RecurringRetireError.HAS_BUDGET)
        }
        catch { recurringRepository.delete(recurring) }.bind()
    }
}

// ----------------------------------------------------------------------------------
// Installments
// ----------------------------------------------------------------------------------

/**
 * The split, over the real ledger: N postings, one per invoice they land on, written as one unit.
 *
 * The distribution is the production rule's shape — the shares are the total divided by the count,
 * the rounding residue is carried by the first, and each falls on the invoice of the month after the
 * previous. What matters for the tests in this module is that N postings exist and that the tool did
 * not decide any of it.
 */
internal class WorldAddInstallment(
    private val transactionRepository: ITransactionRepository,
    private val installmentRepository: IInstallmentRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val createInvoice: CreateInvoiceUseCase,
    private val clock: Clock,
) : AddInstallmentUseCase {

    override suspend fun invoke(
        form: TransactionForm,
        installments: Int,
    ): Either<Throwable, List<Transaction>> = either {
        ensure(installments > 1) { InstallmentException(InstallmentError.MinInstallment) }
        val card = ensureNotNull(form.creditCard) {
            InstallmentException(InstallmentError.MissingCreditCard)
        }
        val firstMonth = ensureNotNull(form.invoiceDueMonth) {
            InstallmentException(InstallmentError.MissingInvoice)
        }
        val date = ensureNotNull(runCatching { dayMonthYear.parse(form.date) }.getOrNull()) {
            IllegalArgumentException("That is not a date.")
        }
        ensure(date <= clock.today()) { IllegalArgumentException("The date cannot be in the future.") }

        val total = form.amount.moneyToDouble()
        val share = round(total / installments * 100) / 100
        val first = round((total - share * (installments - 1)) * 100) / 100

        val planId = catch { installmentRepository.createInstallment(installments, total) }.bind()

        val intents = (0 until installments).map { index ->
            val invoice = invoiceRepository
                .invoiceFor(card, firstMonth.after(index), createInvoice)
                .bind()
            TransactionIntent(
                title = form.title,
                date = date,
                installmentId = planId,
                installmentNumber = index + 1,
                legs = listOf(
                    TransactionLeg(
                        type = TransactionType.EXPENSE,
                        amount = if (index == 0) first else share,
                        accountId = card.accountId,
                        dimensionId = invoice.dimensionId,
                    ),
                ),
                contra = contraLegFor(TransactionType.EXPENSE, form.category),
            )
        }

        catch { transactionRepository.createTransactions(intents) }.bind()
    }
}

internal class WorldUpdateInstallment(
    private val installmentRepository: IInstallmentRepository,
) : UpdateInstallmentUseCase {
    override suspend fun invoke(
        installmentId: Long,
        count: Int,
        totalAmount: Double,
    ): Either<Throwable, Installment> = either {
        val installment = ensureNotNull(installmentRepository.getInstallmentById(installmentId)) {
            InstallmentException(InstallmentError.NotFound)
        }
        ensure(count > 0) { InstallmentException(InstallmentError.NonPositiveCount) }
        ensure(totalAmount > 0.0) { InstallmentException(InstallmentError.NonPositiveTotal) }
        catch { installmentRepository.updateInstallment(installmentId, count, totalAmount) }.bind()
        installment.copy(count = count, totalAmount = totalAmount)
    }
}

internal class WorldDeleteInstallment(
    private val transactionRepository: ITransactionRepository,
    private val installmentRepository: IInstallmentRepository,
) : DeleteInstallmentUseCase {
    override suspend fun invoke(
        installmentId: Long,
        withoutCopy: Boolean,
    ): Either<Throwable, Unit> = either {
        ensureNotNull(installmentRepository.getInstallmentById(installmentId)) {
            InstallmentException(InstallmentError.NotFound)
        }
        catch {
            transactionRepository.deleteTransactionsByIds(
                transactionRepository.getAllTransactions()
                    .filter { it.installmentId == installmentId }
                    .map { it.id },
            )
            installmentRepository.deleteInstallmentById(installmentId)
        }.bind()
    }
}

// ----------------------------------------------------------------------------------
// Invoices
// ----------------------------------------------------------------------------------

internal class WorldCreateInvoice(
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
) : CreateInvoiceUseCase {
    override suspend fun invoke(
        creditCardId: Long,
        dueMonth: YearMonth,
    ): Either<Throwable, Invoice> = either {
        val card = ensureNotNull(creditCardRepository.getCreditCardById(creditCardId)) {
            InvoiceException(InvoiceError.CreditCardNotFound)
        }
        val invoices = invoiceRepository.getInvoicesByCreditCard(card.id)
        ensure(invoices.none { it.dueMonth == dueMonth }) {
            InvoiceException(InvoiceError.AlreadyExists)
        }
        val open = ensureNotNull(invoices.firstOrNull { it.status.isOpen }) {
            InvoiceException(InvoiceError.NoOpenInvoice)
        }
        val window = card.invoiceWindowFor(dueMonth)
        catch {
            invoiceRepository.insert(
                Invoice(
                    creditCard = card,
                    openingMonth = window.openingMonth,
                    closingMonth = window.closingMonth,
                    dueMonth = dueMonth,
                    status = if (dueMonth < open.dueMonth) {
                        Invoice.Status.RETROACTIVE
                    } else {
                        Invoice.Status.FUTURE
                    },
                ),
            )
        }.bind()
    }
}

/**
 * Removal of a cycle that never lived — the rule is `Invoice.Status.isDeletable`, read here rather
 * than restated, because the point of a test in this module is that an open or closed cycle is
 * refused and told why.
 */
internal class WorldDeleteFutureInvoice(
    private val invoiceRepository: IInvoiceRepository,
    private val transactionRepository: ITransactionRepository,
) : DeleteFutureInvoiceUseCase {
    override suspend fun invoke(
        invoiceId: Long,
        withoutCopy: Boolean,
    ): Either<InvoiceException, Unit> = either {
        val invoice = ensureNotNull(invoiceRepository.getInvoiceById(invoiceId)) {
            InvoiceException(InvoiceError.NotFound)
        }
        ensure(invoice.status.isDeletable) { InvoiceException(InvoiceError.CannotDeleteInvoice) }

        transactionRepository.getAllTransactions()
            .filter { transaction -> transaction.entries.any { it.dimensionId == invoice.dimensionId } }
            .forEach { transactionRepository.deleteTransactionById(it.id) }

        invoiceRepository.deleteById(invoiceId)
    }
}
