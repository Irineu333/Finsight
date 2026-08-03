package com.neoutils.finsight

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.di.appModules
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * The whole app over a database in memory: the real Koin graph, the real repositories,
 * the real write boundary.
 *
 * The two gates of design D29 need it because what they assert is not about one use
 * case — it is about **which currency a figure comes out in**, from the entry the
 * boundary wrote to the string a card renders. A fake ledger would answer whatever the
 * test seeded it with, which is precisely the question being asked.
 *
 * The base currency is a parameter and never the machine's: `Settings()` on the JVM
 * reads the developer's own preferences, and the gates are about what the app shows for
 * a *given* base.
 */
internal class AppLedgerHarness(
    baseCurrency: String,
    /**
     * Extra bindings, layered over the real graph — how a gate substitutes a port whose
     * real implementation would reach outside the process, such as the remote rate
     * source. Everything else stays the real thing, which is the point of this harness.
     */
    overrides: Module = module { },
) {

    private val databaseFile: File = File.createTempFile("finsight-gate", ".db")
        .also { it.delete(); it.deleteOnExit() }

    private val koin: Koin = koinApplication {
        modules(
            appModules + module {
                single<RoomDatabase.Builder<AppDatabase>> {
                    // A file of its own per harness. An in-memory database is shared
                    // across builders in one JVM, so two gates would read each other's
                    // accounts — and a gate that asserts "one currency in use" would be
                    // reading the other's second one.
                    Room.databaseBuilder<AppDatabase>(name = databaseFile.absolutePath)
                        .setDriver(BundledSQLiteDriver())
                }
                single<Settings> { MapSettings("base_currency" to baseCurrency) }
            } + overrides,
        )
    }.koin

    inline fun <reified T : Any> get(): T = koin.get<T>()

    /**
     * Releases the graph — **mandatory**, and the reason [runApp] exists.
     *
     * `appModules` is a global list of `Module` objects, and a Koin single keeps its
     * instance inside the definition, not inside the container. So a second
     * `koinApplication` over the same modules hands back the *first* one's database,
     * whatever builder it was given: two gates would silently share a chart of
     * accounts, and "one currency in use" would be reading the other's second one.
     */
    fun close() {
        koin.close()
        databaseFile.delete()
    }

    val accounts: IAccountRepository get() = koin.get()
    val transactions: ITransactionRepository get() = koin.get()
    val entries: IEntryRepository get() = koin.get()
    val cards: ICreditCardRepository get() = koin.get()
    val invoices: IInvoiceRepository get() = koin.get()
    val categories: ICategoryRepository get() = koin.get()

    suspend fun account(
        name: String,
        currency: String,
        isDefault: Boolean = false,
    ): Account {
        val id = accounts.insert(
            Account(name = name, type = AccountType.ASSET, currency = currency, isDefault = isDefault),
        )
        return requireNotNull(accounts.getAccountById(id))
    }

    suspend fun card(name: String, currency: String, limit: Double = 5_000.0): CreditCard {
        val id = cards.insert(
            CreditCard(name = name, limit = limit, closingDay = 10, dueDay = 20),
            currency,
        )
        return requireNotNull(cards.getCreditCardById(id))
    }

    /** An open invoice of [card] closing in [month] — with the dimension its store emits. */
    suspend fun invoice(card: CreditCard, month: YearMonth): Invoice {
        val id = invoices.insert(
            Invoice(
                creditCard = card,
                openingMonth = month.minus(1, DateTimeUnit.MONTH),
                closingMonth = month,
                dueMonth = month,
                status = Invoice.Status.OPEN,
            ),
        )
        return requireNotNull(invoices.getInvoiceById(id))
    }

    /** Closes an invoice, which is what makes it payable. */
    suspend fun closeInvoice(invoice: Invoice): Invoice {
        invoices.update(invoice.copy(status = Invoice.Status.CLOSED))
        return requireNotNull(invoices.getInvoiceById(invoice.id))
    }

    suspend fun category(name: String, type: Category.Type = Category.Type.EXPENSE): Category {
        categories.insert(
            Category(name = name, icon = CategoryLazyIcon("food"), type = type, createdAt = 0L),
        )
        return categories.getAllCategories().first { it.name == name }
    }

    /** An expense out of [account], classified by [category] when one is given. */
    suspend fun expense(
        account: Account,
        amount: Double,
        date: LocalDate,
        category: Category? = null,
    ): Transaction = transactions.createTransaction(
        TransactionIntent(
            title = null,
            date = date,
            legs = listOf(
                TransactionLeg(
                    type = TransactionType.EXPENSE,
                    amount = amount,
                    accountId = account.id,
                ),
            ),
            contra = ContraLeg(
                nature = AccountType.EXPENSE,
                dimensionId = category?.dimensionId,
            ),
        ),
    )

    /** A purchase on [card], landing on [invoice]'s sub-ledger. */
    suspend fun cardExpense(
        card: CreditCard,
        invoice: Invoice,
        amount: Double,
        date: LocalDate,
        category: Category? = null,
    ): Transaction = transactions.createTransaction(
        TransactionIntent(
            title = null,
            date = date,
            legs = listOf(
                TransactionLeg(
                    type = TransactionType.EXPENSE,
                    amount = amount,
                    accountId = card.accountId,
                    dimensionId = invoice.dimensionId,
                ),
            ),
            contra = ContraLeg(
                nature = AccountType.EXPENSE,
                dimensionId = category?.dimensionId,
            ),
        ),
    )

    suspend fun income(account: Account, amount: Double, date: LocalDate): Transaction =
        transactions.createTransaction(
            TransactionIntent(
                title = null,
                date = date,
                legs = listOf(
                    TransactionLeg(
                        type = TransactionType.INCOME,
                        amount = amount,
                        accountId = account.id,
                    ),
                ),
                contra = ContraLeg(nature = AccountType.INCOME),
            ),
        )
}


/**
 * Runs [body] over an app of its own, and releases it afterwards — see
 * [AppLedgerHarness.close] for why releasing is not optional.
 */
internal fun runApp(
    baseCurrency: String,
    overrides: Module = module { },
    body: suspend AppLedgerHarness.() -> Unit,
) = runTest {
    val app = AppLedgerHarness(baseCurrency, overrides)
    try {
        app.body()
    } finally {
        app.close()
    }
}
