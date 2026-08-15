package com.neoutils.finsight.mcp.di

import com.neoutils.finsight.feature.mcp.api.IMcpServerStateSource
import com.neoutils.finsight.mcp.McpServerController
import com.neoutils.finsight.mcp.McpServerStateSource
import com.neoutils.finsight.mcp.contract.MoneyPayloadFactory
import com.neoutils.finsight.mcp.contract.ToolRegistry
import com.neoutils.finsight.mcp.prompt.PromptRegistry
import com.neoutils.finsight.mcp.prompt.userFlowPrompts
import com.neoutils.finsight.mcp.resource.ResourceRegistry
import com.neoutils.finsight.mcp.resource.orientationResources
import com.neoutils.finsight.mcp.tool.AggregateTransactionsTool
import com.neoutils.finsight.mcp.tool.DeleteTransactionsTool
import com.neoutils.finsight.mcp.tool.GetOverviewTool
import com.neoutils.finsight.mcp.tool.ListAccountsTool
import com.neoutils.finsight.mcp.tool.ListBudgetsTool
import com.neoutils.finsight.mcp.tool.ListCategoriesTool
import com.neoutils.finsight.mcp.tool.ListInstallmentsTool
import com.neoutils.finsight.mcp.tool.ListInvoicesTool
import com.neoutils.finsight.mcp.tool.ListRecurringTool
import com.neoutils.finsight.mcp.tool.ListTransactionsTool
import com.neoutils.finsight.mcp.tool.PreviewTransactionsTool
import com.neoutils.finsight.mcp.tool.ProbableDuplicates
import com.neoutils.finsight.mcp.tool.RecordTransactionsTool
import com.neoutils.finsight.mcp.tool.UpdateTransactionsTool
import com.neoutils.finsight.mcp.server.DeclaredClientName
import com.neoutils.finsight.mcp.write.ActivityRecorder
import com.neoutils.finsight.mcp.write.IdempotencyStore
import com.neoutils.finsight.mcp.write.TransactionItemResolver
import org.koin.dsl.module
import kotlin.time.ExperimentalTime

/**
 * The MCP server, assembled: the thirteen tools, the orientation documents, the user's flows, the
 * registry the server consults, the idempotency store, the activity journal's recorder and the
 * controller that owns the socket.
 *
 * **Every dependency below is resolved from the rest of the app.** Nothing here is a second
 * implementation of anything: each tool reads through the same repository the screens read
 * through, and each write goes through the same use case a screen invokes. That is what makes
 * this module an assembly and not a parallel application.
 *
 * The tools, the two write helpers and the controller are `single` because sharing matters:
 * the three orientation documents answer with **the same instances** the corresponding tools do,
 * the idempotency store would honour no key if each tool held its own, and a second controller
 * would be a second socket.
 *
 * It is bound on every target, and that is deliberate: `appModules` is declared in the shared
 * shell's `commonMain`, so a module that existed only on the JVM would force that list apart into
 * expect/actual. On Android and iOS the controller's actual is inert — it is constructed, it
 * listens to nothing, and its state never leaves `Stopped`.
 */
@OptIn(ExperimentalTime::class)
val mcpModule = module {

    // ------------------------------------------------------------------ shared shape

    single { MoneyPayloadFactory(consolidateMoney = get(), exchangeRates = get()) }

    // ------------------------------------------------------------------ the nine reads

    single {
        GetOverviewTool(
            baseCurrency = get(),
            accounts = get(),
            creditCards = get(),
            invoices = get(),
            entries = get(),
            calculateBalance = get(),
            calculateInvoice = get(),
            calculateAvailableLimit = get(),
            exchangeRates = get(),
            money = get(),
            clock = get(),
        )
    }

    single {
        ListAccountsTool(
            accounts = get(),
            calculateBalance = get(),
            clock = get(),
        )
    }

    single { ListCategoriesTool(categories = get(), clock = get()) }

    single {
        ListTransactionsTool(
            transactions = get(),
            accounts = get(),
            creditCards = get(),
            invoices = get(),
            categories = get(),
            clock = get(),
        )
    }

    single {
        AggregateTransactionsTool(
            entries = get(),
            accounts = get(),
            creditCards = get(),
            categories = get(),
            money = get(),
            clock = get(),
        )
    }

    single {
        ListInvoicesTool(
            invoices = get(),
            creditCards = get(),
            calculateInvoice = get(),
            clock = get(),
        )
    }

    single {
        ListBudgetsTool(
            budgets = get(),
            recurring = get(),
            transactions = get(),
            calculateProgress = get(),
            clock = get(),
        )
    }

    single {
        ListRecurringTool(
            recurring = get(),
            occurrences = get(),
            pending = get(),
            clock = get(),
        )
    }

    single {
        ListInstallmentsTool(
            installments = get(),
            transactions = get(),
            accounts = get(),
            creditCards = get(),
            invoices = get(),
            categories = get(),
            clock = get(),
        )
    }

    // ------------------------------------------------------------------ the write path

    single {
        TransactionItemResolver(
            accounts = get(),
            creditCards = get(),
            categories = get(),
            invoices = get(),
            createTransaction = get(),
            addInstallment = get(),
            transferBetweenAccounts = get(),
            payInvoicePayment = get(),
            adjustBalance = get(),
            adjustInvoice = get(),
        )
    }

    single { ProbableDuplicates(transactions = get(), creditCards = get()) }

    single { IdempotencyStore(clock = get()) }

    single { DeclaredClientName() }

    // The recorder reads the name the transport captured at `initialize`, through the one holder
    // both sides share. Passing `{ null }` here would make every record claim no client ever
    // introduced itself — indistinguishable, in the journal, from a client that did not.
    single {
        ActivityRecorder(
            journal = get(),
            clock = get(),
            declaredClient = { get<DeclaredClientName>().name },
        )
    }

    single {
        RecordTransactionsTool(
            resolver = get(),
            duplicates = get(),
            idempotency = get(),
            activity = get(),
        )
    }

    single {
        UpdateTransactionsTool(
            transactions = get(),
            categories = get(),
            creditCards = get(),
            invoices = get(),
            updateTransaction = get(),
            idempotency = get(),
            activity = get(),
        )
    }

    single {
        DeleteTransactionsTool(
            transactions = get(),
            deleteTransaction = get(),
            idempotency = get(),
            activity = get(),
        )
    }

    // The dry run is listed beside the writes because it answers what a write would do — and it
    // is announced at read-only all the same, because it is honestly read-only and the level
    // hides *writes*.
    single { PreviewTransactionsTool(resolver = get(), duplicates = get()) }

    // ------------------------------------------------------------------ what the server serves

    single {
        ToolRegistry(
            listOf(
                get<GetOverviewTool>(),
                get<ListAccountsTool>(),
                get<ListCategoriesTool>(),
                get<ListTransactionsTool>(),
                get<AggregateTransactionsTool>(),
                get<ListInvoicesTool>(),
                get<ListBudgetsTool>(),
                get<ListRecurringTool>(),
                get<ListInstallmentsTool>(),
                get<RecordTransactionsTool>(),
                get<UpdateTransactionsTool>(),
                get<DeleteTransactionsTool>(),
                get<PreviewTransactionsTool>(),
            ),
        )
    }

    single<ResourceRegistry> {
        orientationResources(
            overview = get<GetOverviewTool>(),
            accounts = get<ListAccountsTool>(),
            categories = get<ListCategoriesTool>(),
        )
    }

    single<PromptRegistry> { userFlowPrompts() }

    // ------------------------------------------------------------------ the socket

    single {
        McpServerController(
            settings = get(),
            tools = get(),
            resources = get(),
            prompts = get(),
            declaredClient = get(),
        )
    }

    single<IMcpServerStateSource> { McpServerStateSource(controller = get()) }
}
