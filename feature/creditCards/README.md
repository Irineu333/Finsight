# `:feature:creditCards`

## Responsabilidade

Modelar cartões de crédito e o ciclo de vida de faturas (open → close → pay → reopen, mais ajustes de saldo de fatura).

## Módulos

- `:feature:creditCards:api`
- `:feature:creditCards:ui`
- `:feature:creditCards:impl`

## Contratos públicos (`:api`)

- **Modelos:** `CreditCard` (id, name, limit, closingDay, dueDay, iconKey, createdAt), `Invoice` (creditCardId, openingMonth, closingMonth, dueMonth, status), `Invoice.Status` (`FUTURE`, `OPEN`, `CLOSED`, `PAID`, `RETROACTIVE`), `InvoiceMonth` (dueMonth, existingInvoice, isNew/isBlocked).
- **Repositórios:** `ICreditCardRepository`, `IInvoiceRepository`.
- **Use cases:** `IGetOrCreateInvoiceForMonthUseCase` (`Either<Throwable, Invoice>`).

## UI compartilhada (`:ui`)

- `CreditCardSelector`, `CreditCardCard`, `InvoiceMonthNavigator`, `InvoiceSelector`, `IInvoiceUiMapper`.

## Implementação (`:impl`)

- **Telas:** `CreditCardsScreen` + `CreditCardsViewModel`, `InvoiceTransactionsScreen` + `InvoiceTransactionsViewModel`.
- **Modais:** `CreditCardFormModal`, `DeleteCreditCardModal`, `CloseInvoiceModal`, `PayInvoiceModal`, `AdvancePaymentModal`, `EditInvoiceBalanceModal`, `ReopenInvoiceModal`, `DeleteFutureInvoiceModal`.
- **Repositórios:** `CreditCardRepository`, `InvoiceRepository`.
- **Use cases:** `GetOrCreateInvoiceForMonthUseCase` e operações de ciclo de fatura.

## Dependências

- `:api`: `:core:utils`.
- `:ui`: `:api`, `:core:ui`.
- `:impl`: `:api`, `:ui`, `:feature:accounts:api`, `:feature:accounts:ui`, `:feature:categories:api`, `:feature:categories:ui`, `:feature:transactions:api`, `:feature:transactions:ui`, `:feature:home:api`, `:core:database`, `:core:ui`, `:core:analytics`, `:core:utils`.
