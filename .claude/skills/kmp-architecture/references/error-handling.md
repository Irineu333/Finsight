# Error Handling with Arrow

## Error Type Structure

Every domain feature defines its own error type in `/domain/error/`.

```kotlin
// Simple enum — for errors with no extra data
enum class AccountError {
    NotFound,
    InsufficientBalance,
    SaveFailed,
    DeleteFailed;

    val message: String get() = when (this) {
        NotFound -> "Account not found"
        InsufficientBalance -> "Insufficient balance"
        SaveFailed -> "Failed to save account"
        DeleteFailed -> "Failed to delete account"
    }
}

fun AccountError.toUiText(): UiText = when (this) {
    AccountError.NotFound -> UiText.StringResource(Res.string.error_account_not_found)
    AccountError.InsufficientBalance -> UiText.StringResource(Res.string.error_insufficient_balance)
    AccountError.SaveFailed -> UiText.StringResource(Res.string.error_save_failed)
    AccountError.DeleteFailed -> UiText.StringResource(Res.string.error_delete_failed)
}

// Sealed class — for errors that carry data
sealed class TransferError {
    data object AccountNotFound : TransferError()
    data object InsufficientBalance : TransferError()
    data class InvalidAmount(val amount: Double) : TransferError()

    val message: String get() = when (this) {
        AccountNotFound -> "Source account not found"
        InsufficientBalance -> "Insufficient balance for transfer"
        is InvalidAmount -> "Invalid transfer amount: $amount"
    }
}

fun TransferError.toUiText(): UiText = when (this) {
    TransferError.AccountNotFound -> UiText.StringResource(Res.string.error_account_not_found)
    TransferError.InsufficientBalance -> UiText.StringResource(Res.string.error_insufficient_balance)
    is TransferError.InvalidAmount -> UiText.StringResource(Res.string.error_invalid_amount)
}
```

**Rule:** `message` is always English, for logging only. Never show `message` in the UI.
Always use `toUiText()` for display.

## Exception Wrapper

Only operation use cases that can throw need an exception wrapper.

```kotlin
class TransferException(val error: TransferError) : Exception(error.message)
```

Use in operation use cases:
```kotlin
class TransferBetweenAccountsUseCase(...) {
    suspend operator fun invoke(params: TransferParams) {
        validateParams(params).getOrElse { throw TransferException(it) }
        // perform transfer...
    }
}
```

Catch in ViewModel:
```kotlin
viewModelScope.launch {
    runCatching { transferUseCase(params) }
        .onFailure { e ->
            val uiText = when (e) {
                is TransferException -> e.error.toUiText()
                else -> UiText.StringResource(Res.string.error_unknown)
            }
            _uiState.update { it.copy(error = uiText) }
        }
}
```

## Arrow Either in Practice

### Basic usage
```kotlin
// Returning Either
suspend fun save(account: Account): Either<AccountError, Unit> =
    Either.catch { dao.upsert(account.toEntity()) }
        .mapLeft { AccountError.SaveFailed }

// Consuming Either
save(account).fold(
    ifLeft = { error -> _uiState.update { it.copy(error = error.toUiText()) } },
    ifRight = { /* success */ }
)
```

### Chaining with flatMap
```kotlin
validateAmount(input)
    .flatMap { amount -> validateAccount(accountId).map { account -> Pair(amount, account) } }
    .flatMap { (amount, account) -> repository.transfer(account, amount) }
    .fold(
        ifLeft = { error -> _uiState.update { it.copy(error = error.toUiText()) } },
        ifRight = { _action.send(Action.TransferSuccess) }
    )
```

### Either.catch for wrapping exceptions
```kotlin
Either.catch { riskyOperation() }
    .mapLeft { throwable -> MyError.OperationFailed } // converts any exception to your error type
```

### getOrElse for defaults
```kotlin
val account = repository.findById(id).getOrElse { return }
// account is non-null after this line
```

## UiText

A sealed class bridging domain errors and Compose string resources.

```kotlin
sealed class UiText {
    data class DynamicString(val value: String) : UiText()
    data class StringResource(val res: StringResource) : UiText()

    @Composable
    fun asString(): String = when (this) {
        is DynamicString -> value
        is StringResource -> stringResource(res)
    }
}
```

Usage in Compose:
```kotlin
uiState.error?.let { error ->
    Text(text = error.asString())
}
```

## Logging vs UI Display

```kotlin
// For logging (Crashlytics, Timber, etc.)
Timber.e(error.message)

// For UI display
_uiState.update { it.copy(error = error.toUiText()) }
```

Never log `toUiText()` output — it's localized and useless in logs.
Never show `message` in the UI — it's English and not user-friendly.