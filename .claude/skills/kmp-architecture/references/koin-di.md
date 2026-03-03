# Koin DI for KMP

## Module Structure

Organize modules by layer. Each layer exposes its own Koin module.

```
di/
├── AppModule.kt         # top-level: startKoin { modules(...) }
├── DatabaseModule.kt    # Room database, DAOs, repository implementations
├── DomainModule.kt      # use cases
└── UiModule.kt          # ViewModels (or declared per-screen)
```

## Registration Patterns

| Builder | Scope | Use for |
|---------|-------|---------|
| `single { }` | App lifetime | Repositories, Database, DAOs |
| `factory { }` | New per injection | Use cases (stateless) |
| `viewModel { }` | ViewModel lifetime | ViewModels |
| `scoped { }` | Custom scope lifetime | Rarely needed |

```kotlin
val databaseModule = module {
    // Database — singleton
    single {
        Room.databaseBuilder(get(), AppDatabase::class.java, "finance.db").build()
    }

    // DAOs — derived from the singleton database
    single { get<AppDatabase>().accountDao() }
    single { get<AppDatabase>().transactionDao() }

    // Repository implementations — singleton
    single<AccountRepository> { AccountRepositoryImpl(get(), get()) }
    single<TransactionRepository> { TransactionRepositoryImpl(get(), get()) }
}

val domainModule = module {
    // Use cases — factory (new instance each time)
    factory { GetAccountsUseCase(get()) }
    factory { CreateTransactionUseCase(get()) }
    factory { ValidateTransferParamsUseCase() }
    factory { TransferBetweenAccountsUseCase(get(), get()) }
}

val uiModule = module {
    viewModel { DashboardViewModel(get(), get()) }
    viewModel { parameters -> TransactionDetailViewModel(get(), parameters.get()) }
}
```

## KMP Setup

For Kotlin Multiplatform, use `expect/actual` for platform-specific DI setup.

```kotlin
// commonMain
expect fun platformModule(): Module

// androidMain
actual fun platformModule() = module {
    single<Context> { androidContext() }
    single { get<Context>().getSharedPreferences("prefs", Context.MODE_PRIVATE) }
}

// desktopMain
actual fun platformModule() = module {
    // Desktop-specific bindings
}
```

Starting Koin on Android:
```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MyApplication)
            modules(databaseModule, domainModule, uiModule, platformModule())
        }
    }
}
```

Starting Koin on Desktop:
```kotlin
fun main() {
    startKoin {
        modules(databaseModule, domainModule, uiModule, platformModule())
    }
    // ...
}
```

## Injecting in Composables

```kotlin
// Recommended: inject via ViewModel, not directly in composable
@Composable
fun AccountScreen(
    viewModel: AccountViewModel = koinViewModel()
) { ... }

// For non-ViewModel injection (rare — prefer ViewModel)
@Composable
fun SomeComposable() {
    val repository: AccountRepository = koinInject() // use sparingly
}
```

## Injecting with Parameters (ViewModel with ID)

```kotlin
// Declaration
viewModel { parameters ->
    TransactionDetailViewModel(
        transactionId = parameters.get(),
        getTransaction = get()
    )
}

// Usage
@Composable
fun TransactionDetailScreen(transactionId: Long) {
    val viewModel: TransactionDetailViewModel = koinViewModel(
        parameters = { parametersOf(transactionId) }
    )
}
```

## Testing with Koin

```kotlin
class AccountViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(
            module {
                single<AccountRepository> { FakeAccountRepository() }
                factory { GetAccountsUseCase(get()) }
                viewModel { AccountViewModel(get()) }
            }
        )
    }

    private val viewModel: AccountViewModel by inject()

    @Test
    fun `loads accounts on init`() = runTest { ... }
}
```

## Anti-patterns

```kotlin
// ❌ Injecting ViewModel in non-Composable context
class SomeClass {
    val viewModel: MyViewModel by inject() // ❌ ViewModels need a scope
}

// ❌ Registering use cases as single {}
single { GetAccountsUseCase(get()) } // ❌ use factory {} instead

// ❌ Registering ViewModels as factory {}
factory { DashboardViewModel(get()) } // ❌ use viewModel {} — loses SavedStateHandle integration

// ❌ Circular dependencies
single<A> { AImpl(get<B>()) }
single<B> { BImpl(get<A>()) } // ❌ Koin will throw at runtime
```