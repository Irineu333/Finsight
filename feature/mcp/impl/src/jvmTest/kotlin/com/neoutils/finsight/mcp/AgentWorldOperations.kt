@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.neoutils.finsight.mcp

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.error.CategoryError
import com.neoutils.finsight.domain.error.CreditCardError
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.error.RecurringError
import com.neoutils.finsight.domain.error.TransferError
import com.neoutils.finsight.domain.error.TransferException
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.exception.AccountNotAdjustedException
import com.neoutils.finsight.domain.exception.CategoryException
import com.neoutils.finsight.domain.exception.CreditCardException
import com.neoutils.finsight.domain.exception.InvoiceNotAdjustedException
import com.neoutils.finsight.domain.exception.RecurringException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.dueMonthFor
import com.neoutils.finsight.domain.model.reopenSuccessor
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finsight.domain.usecase.AdjustInvoiceUseCase
import com.neoutils.finsight.domain.usecase.AdvanceInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.ArchiveAccountUseCase
import com.neoutils.finsight.domain.usecase.ArchiveCategoryUseCase
import com.neoutils.finsight.domain.usecase.ArchiveCreditCardUseCase
import com.neoutils.finsight.domain.usecase.ArchiveRecurringUseCase
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CloseInvoiceUseCase
import com.neoutils.finsight.domain.usecase.ConfirmRecurringUseCase
import com.neoutils.finsight.domain.usecase.CreateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.HarvestExchangeRateUseCase
import com.neoutils.finsight.domain.usecase.OpenInvoiceUseCase
import com.neoutils.finsight.domain.usecase.PayInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.PayInvoiceUseCase
import com.neoutils.finsight.domain.usecase.ValidateInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.ReopenInvoiceUseCase
import com.neoutils.finsight.domain.usecase.SetDefaultAccountUseCase
import com.neoutils.finsight.domain.usecase.SkipRecurringUseCase
import com.neoutils.finsight.domain.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveAccountUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveCategoryUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveCreditCardUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveRecurringUseCase
import com.neoutils.finsight.domain.usecase.UpdateAdvanceInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.UpdateTransferUseCase
import com.neoutils.finsight.extension.contraLegFor
import com.neoutils.finsight.extension.monthsUntil
import com.neoutils.finsight.extension.naturalBalanceOf
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The **operation** use cases the fourth family calls, rebuilt over the real ledger.
 *
 * Same boundary and same discipline as `AgentWorldWrites`: their implementations live in each
 * feature's `impl`, which an `impl` may not depend on, so what stands in is the *implementation* and
 * never the ledger. Every posting below goes through the production `TransactionRepository` and its
 * `LedgerEntryWriter`, so `Σ = 0`, the dimension landing rule and the closure guards are the app's
 * own — which is what lets a test say "the payment left the account" and mean the ledger.
 *
 * **The rate is harvested here exactly as the app harvests it**, through the real
 * [HarvestExchangeRateUseCase] over the world's own archive. That is deliberate: a stand-in that
 * skipped it would let `transfer` pass a test about a rate nobody derived.
 */

// ----------------------------------------------------------------------------------
// Invoices — paying, and moving a cycle through its life
// ----------------------------------------------------------------------------------

/**
 * ⚠️ **The status half of paying, and nothing else.**
 *
 * It exists here for the two callers that legitimately have nothing to write — closing an invoice
 * that owes zero, and the step that follows a payment already posted — and for no others. A tool
 * reaching for it directly is the defect the family's first test is built to catch.
 */
internal class WorldPayInvoice(
    private val invoiceRepository: IInvoiceRepository,
    private val clock: Clock,
) : PayInvoiceUseCase {
    override suspend fun invoke(
        invoiceId: Long,
        paidAt: LocalDate,
    ): Either<InvoiceException, Invoice> = either {
        val invoice = ensureNotNull(invoiceRepository.getInvoiceById(invoiceId)) {
            InvoiceException(InvoiceError.NotFound)
        }
        ValidateInvoicePaymentUseCase()(invoice = invoice, date = paidAt, today = clock.today())
            .mapLeft(::InvoiceException)
            .bind()

        invoice.copy(status = Invoice.Status.PAID, paidAt = paidAt)
            .also { invoiceRepository.update(it) }
    }
}

/**
 * Paying a bill: the posting **and** the status, in that order.
 *
 * The money leaves the account undimensioned and only the card's leg carries the invoice's
 * sub-ledger, or the two would cancel it out. What the invoice owes stays in the card's currency
 * whatever the paying account is denominated in, and the residue of a crossing is the write
 * boundary's to place.
 */
internal class WorldPayInvoicePayment(
    private val clock: Clock,
    private val transactionRepository: ITransactionRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val accountRepository: IAccountRepository,
    private val calculateInvoice: CalculateInvoiceUseCase,
    private val payInvoice: PayInvoiceUseCase,
    private val harvestExchangeRate: HarvestExchangeRateUseCase,
) : PayInvoicePaymentUseCase {

    override suspend fun invoke(
        invoiceId: Long,
        date: LocalDate,
        accountId: Long,
        paidAmount: Double?,
    ): Either<Throwable, Invoice> = either {
        val invoice = ensureNotNull(invoiceRepository.getInvoiceById(invoiceId)) {
            InvoiceException(InvoiceError.NotFound)
        }
        val account = ensureNotNull(accountRepository.getAccountById(accountId)) {
            AccountException(AccountError.NOT_FOUND)
        }
        // The same obstacle production reads, and read here for the same reason: before the
        // posting, so a refusal cannot arrive once the money has already left.
        ValidateInvoicePaymentUseCase()(invoice = invoice, date = date, today = clock.today())
            .mapLeft(::InvoiceException)
            .bind()

        val owed = calculateInvoice(invoice)
        ensure(owed > 0.0) { InvoiceException(InvoiceError.InvoiceNotInDebt) }

        val leaving = paidAmount ?: owed
        ensure(leaving > 0.0) { InvoiceException(InvoiceError.InvoiceNotInDebt) }

        catch {
            transactionRepository.createTransaction(
                TransactionIntent(
                    title = null,
                    date = date,
                    legs = listOf(
                        TransactionLeg(TransactionType.EXPENSE, leaving, account.id),
                        TransactionLeg(
                            type = TransactionType.INCOME,
                            amount = owed,
                            accountId = invoice.creditCard.accountId,
                            dimensionId = invoice.dimensionId,
                        ),
                    ),
                ),
            )
        }.bind()

        catch {
            invoice.creditCard.currency?.let { cardCurrency ->
                harvestExchangeRate(
                    sourceAmount = leaving,
                    sourceCurrency = account.currency,
                    targetAmount = owed,
                    targetCurrency = cardCurrency,
                    date = date,
                )
            }
        }

        payInvoice(invoiceId, date).bind()
    }
}

/**
 * What makes a **partial** payment admissible, stated once for both of the doubles that write one.
 *
 * The invoices it accepts are the ones still taking spending, which `Invoice.acceptsPartialPayment`
 * decides — the predicate itself, read from `core/model`, and not a list of statuses restated here.
 * The ceiling leaves the rewritten operation's own contribution out, which is what a correction is
 * judged by.
 */
internal class WorldValidateAdvancePayment(
    private val invoiceRepository: IInvoiceRepository,
    private val accountRepository: IAccountRepository,
    private val calculateInvoice: CalculateInvoiceUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        invoiceId: Long,
        amount: Double,
        date: LocalDate,
        accountId: Long,
        paidAmount: Double?,
        excluding: Long? = null,
    ): Either<Throwable, Pair<Invoice, Account>> = either {
        ensure(amount > 0) { InvoiceException(InvoiceError.NegativeAmount) }
        ensure(paidAmount == null || paidAmount > 0) { InvoiceException(InvoiceError.NegativeAmount) }

        val invoice = ensureNotNull(invoiceRepository.getInvoiceById(invoiceId)) {
            InvoiceException(InvoiceError.NotFound)
        }
        val account = ensureNotNull(accountRepository.getAccountById(accountId)) {
            AccountException(AccountError.NOT_FOUND)
        }

        ensure(invoice.acceptsPartialPayment) {
            InvoiceException(InvoiceError.InvoiceNotPartiallyPayable)
        }
        ensure(date >= invoice.openingDate && date <= invoice.closingDate) {
            InvoiceException(InvoiceError.DateOutsideInvoicePeriod)
        }
        ensure(date <= clock.today()) { InvoiceException(InvoiceError.DateInFuture) }

        val ceiling = calculateInvoice(invoice, excluding = excluding)
        ensure(ceiling > 0.0) { InvoiceException(InvoiceError.InvoiceNotInDebt) }
        ensure(amount <= ceiling) { InvoiceException(InvoiceError.AmountExceedsInvoice) }

        invoice to account
    }
}

/** Paying part of an open cycle: the ceiling is on the card's side, never on the account's. */
internal class WorldAdvanceInvoicePayment(
    private val transactionRepository: ITransactionRepository,
    private val validatePayment: WorldValidateAdvancePayment,
    private val harvestExchangeRate: HarvestExchangeRateUseCase,
) : AdvanceInvoicePaymentUseCase {

    override suspend fun invoke(
        invoiceId: Long,
        amount: Double,
        date: LocalDate,
        accountId: Long,
        paidAmount: Double?,
    ): Either<Throwable, Transaction> = either {
        val (invoice, account) = validatePayment(
            invoiceId = invoiceId,
            amount = amount,
            date = date,
            accountId = accountId,
            paidAmount = paidAmount,
        ).bind()

        val leaving = paidAmount ?: amount

        val transaction = catch {
            transactionRepository.createTransaction(
                TransactionIntent(
                    title = null,
                    date = date,
                    legs = paymentLegs(invoice, account, leaving, amount),
                ),
            )
        }.bind()

        harvest(invoice, account, leaving, amount, date)

        transaction
    }

    private suspend fun harvest(
        invoice: Invoice,
        account: Account,
        leaving: Double,
        settling: Double,
        date: LocalDate,
    ) {
        catch {
            invoice.creditCard.currency?.let { cardCurrency ->
                harvestExchangeRate(
                    sourceAmount = leaving,
                    sourceCurrency = account.currency,
                    targetAmount = settling,
                    targetCurrency = cardCurrency,
                    date = date,
                )
            }
        }
    }
}

/**
 * The same operation, rewritten over one that already exists: the legs are replaced, the
 * transaction keeps its identity, and the title it carries is preserved because the payment form
 * does not exhibit one.
 */
internal class WorldUpdateAdvanceInvoicePayment(
    private val transactionRepository: ITransactionRepository,
    private val validatePayment: WorldValidateAdvancePayment,
    private val harvestExchangeRate: HarvestExchangeRateUseCase,
) : UpdateAdvanceInvoicePaymentUseCase {

    override suspend fun invoke(
        transactionId: Long,
        invoiceId: Long,
        amount: Double,
        date: LocalDate,
        accountId: Long,
        paidAmount: Double?,
    ): Either<Throwable, Unit> = either {
        val (invoice, account) = validatePayment(
            invoiceId = invoiceId,
            amount = amount,
            date = date,
            accountId = accountId,
            paidAmount = paidAmount,
            excluding = transactionId,
        ).bind()

        val transaction = ensureNotNull(transactionRepository.getTransactionById(transactionId)) {
            InvoiceException(InvoiceError.NotFound)
        }

        val leaving = paidAmount ?: amount

        catch {
            transactionRepository.updateTransaction(
                id = transactionId,
                title = transaction.title,
                date = date,
                legs = paymentLegs(invoice, account, leaving, amount),
                contra = null,
            )
        }.bind()

        catch {
            invoice.creditCard.currency?.let { cardCurrency ->
                harvestExchangeRate(
                    sourceAmount = leaving,
                    sourceCurrency = account.currency,
                    targetAmount = amount,
                    targetCurrency = cardCurrency,
                    date = date,
                )
            }
        }

        Unit
    }
}

/**
 * The shape a payment takes in the ledger: the money leaves the account **undimensioned**, and the
 * card's `LIABILITY` leg carries the invoice's dimension. Written once, like production's
 * `WriteInvoicePaymentUseCase`, so registering one and correcting one cannot state it differently.
 */
private fun paymentLegs(
    invoice: Invoice,
    account: Account,
    leaving: Double,
    settling: Double,
) = listOf(
    TransactionLeg(TransactionType.EXPENSE, leaving, account.id),
    TransactionLeg(
        type = TransactionType.INCOME,
        amount = settling,
        accountId = invoice.creditCard.accountId,
        dimensionId = invoice.dimensionId,
    ),
)

/** Closing a cycle: the successor opens, and only an invoice owing nothing is settled by it. */
internal class WorldCloseInvoice(
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoice: CalculateInvoiceUseCase,
    private val payInvoice: PayInvoiceUseCase,
    private val openInvoice: OpenInvoiceUseCase,
) : CloseInvoiceUseCase {

    override suspend fun invoke(
        invoiceId: Long,
        closedAt: LocalDate,
    ): Either<InvoiceException, Invoice> = either {
        val invoice = ensureNotNull(invoiceRepository.getInvoiceById(invoiceId)) {
            InvoiceException(InvoiceError.NotFound)
        }
        ensure(invoice.status != Invoice.Status.PAID) {
            InvoiceException(InvoiceError.CannotClosePaidInvoice)
        }
        ensure(invoice.isClosable) { InvoiceException(InvoiceError.AlreadyClosed) }
        ensure(closedAt.yearMonth == invoice.closingMonth) {
            InvoiceException(InvoiceError.CannotCloseOutsideClosingMonth)
        }

        val owed = calculateInvoice(invoice)
        ensure(owed >= 0) { InvoiceException(InvoiceError.NegativeBalance) }

        if (invoice.status.isRetroactive && owed == 0.0) {
            return@either payInvoice(invoice.id, closedAt).bind()
        }

        val closed = invoice.copy(status = Invoice.Status.CLOSED, closedAt = closedAt)
            .also { invoiceRepository.update(it) }

        if (!invoice.status.isRetroactive) {
            openInvoice(invoice.creditCard.id, invoice.closingMonth)
        }

        if (owed == 0.0) payInvoice(closed.id, closedAt).bind() else closed
    }
}

/** Opening a cycle: a future invoice for the month is promoted, never duplicated. */
internal class WorldOpenInvoice(
    private val invoiceRepository: IInvoiceRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val clock: Clock,
) : OpenInvoiceUseCase {

    override suspend fun invoke(
        creditCardId: Long,
        openingMonth: YearMonth,
    ): Either<InvoiceException, Invoice> = either {
        val card = ensureNotNull(creditCardRepository.getCreditCardById(creditCardId)) {
            InvoiceException(InvoiceError.CreditCardNotFound)
        }

        val closingMonth = openingMonth.plusMonth()
        val existing = invoiceRepository.getInvoicesByCreditCard(card.id)

        existing.find { it.status == Invoice.Status.FUTURE && it.openingMonth == openingMonth }
            ?.let { future ->
                return@either future.copy(status = Invoice.Status.OPEN, openedAt = clock.today())
                    .also { invoiceRepository.update(it) }
            }

        ensure(
            existing.none { openingMonth < it.closingMonth && closingMonth > it.openingMonth },
        ) {
            InvoiceException(InvoiceError.OverlappingInvoice)
        }

        invoiceRepository.insert(
            Invoice(
                creditCard = card,
                openingMonth = openingMonth,
                closingMonth = closingMonth,
                dueMonth = card.dueMonthFor(closingMonth),
                status = Invoice.Status.OPEN,
                openedAt = clock.today(),
            ),
        )
    }
}

/** Reopening: only the latest closed invoice, and its successor goes back to future. */
internal class WorldReopenInvoice(
    private val invoiceRepository: IInvoiceRepository,
) : ReopenInvoiceUseCase {

    override suspend fun invoke(invoiceId: Long): Either<InvoiceException, Invoice> = either {
        val invoice = ensureNotNull(invoiceRepository.getInvoiceById(invoiceId)) {
            InvoiceException(InvoiceError.NotFound)
        }
        ensure(invoice.status != Invoice.Status.OPEN) { InvoiceException(InvoiceError.AlreadyOpen) }
        ensure(invoice.status != Invoice.Status.PAID) {
            InvoiceException(InvoiceError.CannotReopenPaidInvoice)
        }

        val successor = invoice.reopenSuccessor(
            invoiceRepository.getInvoicesByCreditCard(invoice.creditCard.id),
        )
        ensureNotNull(successor?.takeIf { it.status == Invoice.Status.OPEN }) {
            InvoiceException(InvoiceError.CannotReopenInvoice)
        }

        invoiceRepository.update(successor.copy(status = Invoice.Status.FUTURE))

        invoice.copy(status = Invoice.Status.OPEN, closedAt = null, paidAt = null)
            .also { invoiceRepository.update(it) }
    }
}

/**
 * Correcting what an invoice owes, by posting the difference.
 *
 * The idempotency is the production rule's, and it is the point of a test in this module: the
 * adjustment already written on that date is rewritten **from its own ledger leg**, so correcting
 * twice lands on the target rather than past it.
 */
internal class WorldAdjustInvoice(
    private val invoiceRepository: IInvoiceRepository,
    private val transactionRepository: ITransactionRepository,
    private val calculateInvoice: CalculateInvoiceUseCase,
) : AdjustInvoiceUseCase {

    override suspend fun invoke(
        invoiceId: Long,
        target: Double,
        adjustmentDate: LocalDate,
    ): Either<Throwable, Unit> = either {
        val invoice = ensureNotNull(invoiceRepository.getInvoiceById(invoiceId)) {
            InvoiceException(InvoiceError.NotFound)
        }
        val current = calculateInvoice(invoice)
        ensure(target != current) { InvoiceNotAdjustedException() }

        catch {
            val existing = transactionRepository
                .observeTransactionsBy(date = adjustmentDate, dimensionId = invoice.dimensionId)
                .first()
                .firstOrNull { it.entries.any { entry -> entry.account.type == AccountType.EQUITY } }

            val difference = target - current

            if (existing == null) {
                transactionRepository.createTransaction(
                    TransactionIntent(
                        title = null,
                        date = adjustmentDate,
                        legs = listOf(
                            TransactionLeg(
                                type = TransactionType.ADJUSTMENT,
                                amount = -difference,
                                accountId = invoice.creditCard.accountId,
                                dimensionId = invoice.dimensionId,
                            ),
                        ),
                        contra = ContraLeg(AccountType.EQUITY),
                    ),
                )
                return@catch
            }

            val currentAdjustment = existing.entries
                .filter { it.dimensionId == invoice.dimensionId }
                .sumOf { it.amount } / CENTS
            val newAmount = currentAdjustment - difference

            if (newAmount == 0.0) {
                transactionRepository.deleteTransactionById(existing.id)
                return@catch
            }

            transactionRepository.updateTransaction(
                id = existing.id,
                title = existing.title,
                date = existing.date,
                legs = listOf(
                    TransactionLeg(
                        type = TransactionType.ADJUSTMENT,
                        amount = newAmount,
                        accountId = invoice.creditCard.accountId,
                        dimensionId = invoice.dimensionId,
                    ),
                ),
                contra = ContraLeg(AccountType.EQUITY),
            )
        }.bind()
    }
}

// ----------------------------------------------------------------------------------
// Accounts — correcting, moving, electing
// ----------------------------------------------------------------------------------

/** Correcting a balance by posting the difference against an `EQUITY` counter-leg. */
internal class WorldAdjustBalance(
    private val accountRepository: IAccountRepository,
    private val transactionRepository: ITransactionRepository,
    private val calculateBalance: CalculateBalanceUseCase,
) : AdjustBalanceUseCase {

    override suspend fun invoke(
        targetBalance: Double,
        adjustmentDate: LocalDate,
        accountId: Long,
    ): Either<Throwable, Unit> = either {
        ensureNotNull(accountRepository.getAccountById(accountId)) {
            AccountException(AccountError.NOT_FOUND)
        }

        val current = calculateBalance.forAccount(accountId, adjustmentDate)
        ensure(targetBalance != current) { AccountNotAdjustedException() }

        catch {
            val existing = transactionRepository
                .observeTransactionsBy(date = adjustmentDate, accountId = accountId)
                .first()
                .firstOrNull { transaction ->
                    transaction.entries.any { it.account.type == AccountType.EQUITY } &&
                        transaction.entries.any { it.account.id == accountId }
                }

            val difference = targetBalance - current

            if (existing == null) {
                transactionRepository.createTransaction(
                    TransactionIntent(
                        title = null,
                        date = adjustmentDate,
                        legs = listOf(
                            TransactionLeg(TransactionType.ADJUSTMENT, difference, accountId),
                        ),
                        contra = ContraLeg(AccountType.EQUITY),
                    ),
                )
                return@catch
            }

            val newAmount = existing.entries.naturalBalanceOf(accountId) / CENTS + difference

            if (newAmount == 0.0) {
                transactionRepository.deleteTransactionById(existing.id)
                return@catch
            }

            transactionRepository.updateTransaction(
                id = existing.id,
                title = existing.title,
                date = existing.date,
                legs = listOf(TransactionLeg(TransactionType.ADJUSTMENT, newAmount, accountId)),
                contra = ContraLeg(AccountType.EQUITY),
            )
        }.bind()
    }
}

/**
 * What makes a transfer admissible, stated once for both of the doubles that write one.
 *
 * It is the relation `ValidateTransferUseCase` has with `TransferBetweenAccountsUseCase` and
 * `UpdateTransferUseCase` in production, and it is here for the same reason: registering a transfer
 * and correcting one judge it alike, and two copies would drift with nothing to report it.
 */
internal class WorldValidateTransfer(
    private val accountRepository: IAccountRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        sourceAccountId: Long,
        destinationAccountId: Long,
        amount: Double,
        date: LocalDate,
        destinationAmount: Double?,
    ): Either<TransferException, Pair<Account, Account>> = either {
        ensure(amount > 0.0) { TransferException(TransferError.InvalidAmount) }
        ensure(destinationAmount == null || destinationAmount > 0.0) {
            TransferException(TransferError.InvalidAmount)
        }
        ensure(sourceAccountId != destinationAccountId) { TransferException(TransferError.SameAccount) }
        ensure(date <= clock.today()) { TransferException(TransferError.FutureDate) }

        val source = ensureNotNull(accountRepository.getAccountById(sourceAccountId)) {
            TransferException(TransferError.SourceAccountNotFound)
        }
        val destination = ensureNotNull(accountRepository.getAccountById(destinationAccountId)) {
            TransferException(TransferError.DestinationAccountNotFound)
        }

        source to destination
    }
}

/**
 * Money between two of the user's own accounts, with the rate **harvested** from the two ends.
 *
 * No rate is a parameter anywhere on this path, and the harvesting below is the real
 * [HarvestExchangeRateUseCase] rather than a stand-in — which is what makes the assertion that a
 * cross-currency transfer taught the archive a statement about the app.
 */
internal class WorldTransfer(
    private val transactionRepository: ITransactionRepository,
    private val validateTransfer: WorldValidateTransfer,
    private val harvestExchangeRate: HarvestExchangeRateUseCase,
) : TransferBetweenAccountsUseCase {

    override suspend fun invoke(
        sourceAccountId: Long,
        destinationAccountId: Long,
        amount: Double,
        date: LocalDate,
        destinationAmount: Double?,
        title: String?,
    ): Either<TransferException, Transaction> = either {
        val (source, destination) = validateTransfer(
            sourceAccountId = sourceAccountId,
            destinationAccountId = destinationAccountId,
            amount = amount,
            date = date,
            destinationAmount = destinationAmount,
        ).bind()

        val arriving = destinationAmount ?: amount

        val transaction = catch {
            transactionRepository.createTransaction(
                TransactionIntent(
                    title = title,
                    date = date,
                    legs = listOf(
                        TransactionLeg(TransactionType.EXPENSE, amount, source.id),
                        TransactionLeg(TransactionType.INCOME, arriving, destination.id),
                    ),
                ),
            )
        }.mapLeft { TransferException(TransferError.Unknown) }.bind()

        catch {
            harvestExchangeRate(
                sourceAmount = amount,
                sourceCurrency = source.currency,
                targetAmount = arriving,
                targetCurrency = destination.currency,
                date = date,
            )
        }

        transaction
    }
}

/**
 * The same operation, rewritten over one that already exists: the legs are replaced and the
 * transaction keeps its identity.
 */
internal class WorldUpdateTransfer(
    private val transactionRepository: ITransactionRepository,
    private val validateTransfer: WorldValidateTransfer,
    private val harvestExchangeRate: HarvestExchangeRateUseCase,
) : UpdateTransferUseCase {

    override suspend fun invoke(
        transactionId: Long,
        sourceAccountId: Long,
        destinationAccountId: Long,
        amount: Double,
        date: LocalDate,
        title: String?,
        destinationAmount: Double?,
    ): Either<TransferException, Unit> = either {
        val (source, destination) = validateTransfer(
            sourceAccountId = sourceAccountId,
            destinationAccountId = destinationAccountId,
            amount = amount,
            date = date,
            destinationAmount = destinationAmount,
        ).bind()

        ensureNotNull(transactionRepository.getTransactionById(transactionId)) {
            TransferException(TransferError.Unknown)
        }

        val arriving = destinationAmount ?: amount

        catch {
            transactionRepository.updateTransaction(
                id = transactionId,
                title = title,
                date = date,
                legs = listOf(
                    TransactionLeg(TransactionType.EXPENSE, amount, source.id),
                    TransactionLeg(TransactionType.INCOME, arriving, destination.id),
                ),
                contra = null,
            )
        }.mapLeft { TransferException(TransferError.Unknown) }.bind()

        catch {
            harvestExchangeRate(
                sourceAmount = amount,
                sourceCurrency = source.currency,
                targetAmount = arriving,
                targetCurrency = destination.currency,
                date = date,
            )
        }

        Unit
    }
}

/** The exclusive, always-filled role: electing one demotes whoever held it. */
internal class WorldSetDefaultAccount(
    private val accountRepository: IAccountRepository,
) : SetDefaultAccountUseCase {
    override suspend fun invoke(accountId: Long): Either<Throwable, Unit> = either {
        val accounts = accountRepository.getAllAccounts()
        ensure(accounts.any { it.id == accountId }) { AccountException(AccountError.NOT_FOUND) }

        catch {
            accounts.forEach { account ->
                when {
                    account.id == accountId && !account.isDefault ->
                        accountRepository.update(account.copy(isDefault = true))

                    account.id != accountId && account.isDefault ->
                        accountRepository.update(account.copy(isDefault = false))
                }
            }
        }.bind()
    }
}

// ----------------------------------------------------------------------------------
// Recurring — confirming and skipping a cycle
// ----------------------------------------------------------------------------------

/**
 * Confirming one cycle, with the **asymmetry that the fourth family turns on** written out in full.
 *
 * An omitted `amount`, `target`, `account` or `creditCard` falls back to the template. An omitted
 * `title` or `category` falls back to **nothing**, because both are things a user erases. That is
 * the production rule, reproduced here exactly, so a tool that forwarded a `null` rather than
 * pre-filling would post a cycle with neither — and a test in this module would see it.
 */
internal class WorldConfirmRecurring(
    private val recurringRepository: IRecurringRepository,
    private val occurrenceRepository: IRecurringOccurrenceRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val createInvoice: CreateInvoiceUseCase,
    private val clock: Clock,
) : ConfirmRecurringUseCase {

    override suspend fun invoke(
        recurringId: Long,
        date: LocalDate,
        amount: Double?,
        target: TransactionTarget?,
        account: Account?,
        creditCard: CreditCard?,
        invoice: Invoice?,
        title: String?,
        category: Category?,
    ): Either<Throwable, Transaction> = catch {
        val recurring = recurringRepository.getRecurringById(recurringId)
            ?: throw RecurringException(RecurringError.NOT_FOUND)

        val yearMonth = date.yearMonth
        val cycleNumber = Instant
            .fromEpochMilliseconds(recurring.createdAt)
            .toYearMonth()
            .monthsUntil(yearMonth) + 1

        val cycleAmount = amount ?: recurring.amount
        if (cycleAmount <= 0.0) throw RecurringException(RecurringError.AMOUNT_NOT_POSITIVE)
        val cycleTarget = target
            ?: if (recurring.creditCard != null) {
                TransactionTarget.CREDIT_CARD
            } else {
                TransactionTarget.ACCOUNT
            }
        val cycleTitle = title?.trim()?.takeIf { it.isNotBlank() }

        val templateCurrency = recurring.account?.currency ?: recurring.creditCard?.currency

        val intent = if (cycleTarget.isCreditCard) {
            val card = requireNotNull(creditCard ?: recurring.creditCard) {
                "Credit card is required for recurring confirmation"
            }
            rejectIfCurrencyDiffers(templateCurrency, card.currency)

            val targetInvoice = invoice
                ?: invoiceRepository.getInvoicesByCreditCard(card.id)
                    .firstOrNull { it.status.isOpen }
                ?: createInvoice(card.id, yearMonth).getOrElse { throw it }

            TransactionIntent(
                title = cycleTitle,
                date = date,
                recurringId = recurring.id,
                recurringCycle = cycleNumber,
                legs = listOf(
                    TransactionLeg(
                        type = recurring.type,
                        amount = cycleAmount,
                        accountId = card.accountId,
                        dimensionId = targetInvoice.dimensionId,
                    ),
                ),
                contra = contraLegFor(recurring.type, category),
            )
        } else {
            val source = requireNotNull(account ?: recurring.account) {
                "Account is required for recurring confirmation"
            }
            rejectIfCurrencyDiffers(templateCurrency, source.currency)

            TransactionIntent(
                title = cycleTitle,
                date = date,
                recurringId = recurring.id,
                recurringCycle = cycleNumber,
                legs = listOf(TransactionLeg(recurring.type, cycleAmount, source.id)),
                contra = contraLegFor(recurring.type, category),
            )
        }

        occurrenceRepository.confirmCycle(
            intent = intent,
            occurrence = RecurringOccurrence(
                recurringId = recurring.id,
                cycleNumber = cycleNumber,
                yearMonth = yearMonth,
                status = RecurringOccurrence.Status.CONFIRMED,
                effectiveDate = date,
                handledAt = clock.now().toEpochMilliseconds(),
            ),
        )
    }

    private fun rejectIfCurrencyDiffers(templateCurrency: String?, targetCurrency: String?) {
        if (templateCurrency == null || targetCurrency == null) return
        if (templateCurrency != targetCurrency) {
            throw RecurringException(RecurringError.CURRENCY_MISMATCH)
        }
    }
}

/** A pass writes no posting: the month simply stops being offered. */
internal class WorldSkipRecurring(
    private val recurringRepository: IRecurringRepository,
    private val occurrenceRepository: IRecurringOccurrenceRepository,
    private val clock: Clock,
) : SkipRecurringUseCase {

    override suspend fun invoke(recurringId: Long, date: LocalDate): Either<Throwable, Unit> = catch {
        val recurring = recurringRepository.getRecurringById(recurringId)
            ?: throw RecurringException(RecurringError.NOT_FOUND)

        val yearMonth = date.yearMonth
        val cycleNumber = Instant
            .fromEpochMilliseconds(recurring.createdAt)
            .toYearMonth()
            .monthsUntil(yearMonth) + 1

        require(
            occurrenceRepository.getOccurrenceBy(recurring.id, yearMonth)?.status !=
                RecurringOccurrence.Status.CONFIRMED,
        ) {
            "Recurring already confirmed for $yearMonth"
        }

        occurrenceRepository.save(
            RecurringOccurrence(
                recurringId = recurring.id,
                cycleNumber = cycleNumber,
                yearMonth = yearMonth,
                status = RecurringOccurrence.Status.SKIPPED,
                effectiveDate = date,
                handledAt = clock.now().toEpochMilliseconds(),
            ),
        )
        Unit
    }
}

// ----------------------------------------------------------------------------------
// Retiring and restoring the four facades the generic tools reach
// ----------------------------------------------------------------------------------

/**
 * Closing an account, with the guard that makes archiving different from deleting: a **permanent**
 * account holding money is refused, because archiving invents no write-off to zero it.
 *
 * It writes the flag in both places the world keeps it — the real `accounts` row and the in-memory
 * facade — which in the app are one and the same row.
 */
internal class WorldArchiveAccount(
    private val accountRepository: IAccountRepository,
    private val accountDao: AccountDao,
    private val entryRepository: IEntryRepository,
) : ArchiveAccountUseCase {

    override suspend fun invoke(accountId: Long): Either<Throwable, Unit> = either {
        val account = ensureNotNull(accountRepository.getAccountById(accountId)) {
            AccountException(AccountError.NOT_FOUND)
        }
        ensure(!account.isDefault) { AccountException(AccountError.CANNOT_ARCHIVE_DEFAULT) }
        ensure(!account.type.isPermanent || entryRepository.balance(account.id) == 0.0) {
            AccountException(AccountError.HAS_BALANCE)
        }

        catch {
            accountDao.close(account.id)
            accountRepository.update(account.copy(isArchived = true))
        }.bind()
    }
}

internal class WorldUnarchiveAccount(
    private val accountRepository: IAccountRepository,
    private val accountDao: AccountDao,
) : UnarchiveAccountUseCase {

    override suspend fun invoke(accountId: Long): Either<Throwable, Unit> = either {
        ensureNotNull(accountRepository.getAccountById(accountId)) {
            AccountException(AccountError.NOT_FOUND)
        }
        catch {
            accountDao.reopen(accountId)
            accountRepository.reopen(accountId)
        }.bind()
    }
}

/**
 * Closing a card closes the `LIABILITY` row it projects onto — which is where its closure lives, and
 * why archiving a card with a bill still owed is refused by the same balance guard as an account.
 */
internal class WorldArchiveCreditCard(
    private val creditCardRepository: ICreditCardRepository,
    private val accountDao: AccountDao,
    private val entryRepository: IEntryRepository,
) : ArchiveCreditCardUseCase {

    override suspend fun invoke(creditCardId: Long): Either<Throwable, Unit> = either {
        val card = ensureNotNull(creditCardRepository.getCreditCardById(creditCardId)) {
            CreditCardException(CreditCardError.NOT_FOUND)
        }
        ensureNotNull(accountDao.getAccountById(card.accountId)) {
            AccountException(AccountError.NOT_FOUND)
        }
        ensure(entryRepository.balance(card.accountId) == 0.0) {
            AccountException(AccountError.HAS_BALANCE)
        }

        catch {
            accountDao.close(card.accountId)
            creditCardRepository.update(card.copy(isArchived = true))
        }.bind()
    }
}

internal class WorldUnarchiveCreditCard(
    private val creditCardRepository: ICreditCardRepository,
    private val accountDao: AccountDao,
) : UnarchiveCreditCardUseCase {

    override suspend fun invoke(creditCardId: Long): Either<Throwable, Unit> = either {
        val card = ensureNotNull(creditCardRepository.getCreditCardById(creditCardId)) {
            CreditCardException(CreditCardError.NOT_FOUND)
        }
        catch {
            accountDao.reopen(card.accountId)
            creditCardRepository.unarchive(card.accountId)
        }.bind()
    }
}

internal class WorldArchiveCategory(
    private val categoryRepository: ICategoryRepository,
) : ArchiveCategoryUseCase {
    override suspend fun invoke(categoryId: Long): Either<Throwable, Unit> = either {
        ensureNotNull(categoryRepository.getCategoryById(categoryId)) {
            CategoryException(CategoryError.NOT_FOUND)
        }
        catch { categoryRepository.archive(categoryId) }.bind()
    }
}

internal class WorldUnarchiveCategory(
    private val categoryRepository: ICategoryRepository,
) : UnarchiveCategoryUseCase {
    override suspend fun invoke(categoryId: Long): Either<Throwable, Unit> = either {
        ensureNotNull(categoryRepository.getCategoryById(categoryId)) {
            CategoryException(CategoryError.NOT_FOUND)
        }
        catch { categoryRepository.unarchive(categoryId) }.bind()
    }
}

internal class WorldArchiveRecurring(
    private val recurringRepository: IRecurringRepository,
) : ArchiveRecurringUseCase {
    override suspend fun invoke(recurringId: Long): Either<Throwable, Unit> = either {
        val recurring = ensureNotNull(recurringRepository.getRecurringById(recurringId)) {
            RecurringException(RecurringError.NOT_FOUND)
        }
        catch { recurringRepository.update(recurring.copy(isArchived = true)) }.bind()
    }
}

internal class WorldUnarchiveRecurring(
    private val recurringRepository: IRecurringRepository,
) : UnarchiveRecurringUseCase {
    override suspend fun invoke(recurringId: Long): Either<Throwable, Unit> = either {
        val recurring = ensureNotNull(recurringRepository.getRecurringById(recurringId)) {
            RecurringException(RecurringError.NOT_FOUND)
        }
        catch { recurringRepository.update(recurring.copy(isArchived = false)) }.bind()
    }
}

/** Cents to the currency's own unit, the one conversion these doubles perform. */
private const val CENTS = 100.0
