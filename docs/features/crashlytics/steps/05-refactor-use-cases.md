# Etapa 05 — Refatorar use cases

> Parte do plano: [Crashlytics](../plan.md)

---

## O que fazer

Refatorar todos os use cases que podem lançar exceções inesperadas para retornar `Either<Throwable, ...>`, usando `either { catch { } }` do Arrow. Isso garante que nenhuma exceção escape sem rastreamento.

**Critério para o tipo do Left:**

- `Either<XxxError, ...>` — erro é comportamento esperado, exceções são tratadas internamente (validações)
- `Either<Throwable, ...>` — erro é inesperado, exceções podem escapar (operações de I/O, repositórios)

---

## Use cases que precisam de refatoração

### Void sem error handling → `Either<Throwable, ...>`

| Use case | Problema | Mudança |
|----------|----------|---------|
| `AdjustBalanceUseCase` | Void; chama repo.getTransactionsBy, operationRepository.createOperation, repo.update sem catch | Retornar `Either<Throwable, Unit>`; wrapping com `either { catch { } }` |
| `AdjustFinalBalanceUseCase` | Void; delega para AdjustBalanceUseCase | Retornar `Either<Throwable, Unit>`; propagar o Either do AdjustBalanceUseCase |
| `AdjustInitialBalanceUseCase` | Void; delega para AdjustBalanceUseCase | Idem |
| `SetDefaultAccountUseCase` | Void; chama repo.update sem catch | Retornar `Either<Throwable, Unit>`; wrapping com `either { catch { } }` |
| `AdjustInvoiceUseCase` | Void; chama repo diretamente | Idem |
| `DeleteCreditCardUseCase` | Void; chama repo diretamente | Idem |
| `CreateDefaultCategoriesUseCase` | Void; insere categorias em loop | Idem |
| `EnsureDefaultAccountUseCase` | Retorna `Account` plain; chama repo que pode lançar | Retornar `Either<Throwable, Account>` |

### `runCatching` → `either { catch { } }`

| Use case | Problema | Mudança |
|----------|----------|---------|
| `CreateSupportIssueUseCase` | Usa `runCatching { }.fold()` — não idiomático com Arrow | Usar `either { catch { } }`; validações com `ensure` |
| `AddSupportReplyUseCase` | Idem | Idem |

---

## Arquivos afetados

- `domain/usecase/AdjustBalanceUseCase.kt` — `Either<Throwable, Unit>` com `either { catch { } }`
- `domain/usecase/AdjustFinalBalanceUseCase.kt` — propagar Either do AdjustBalanceUseCase
- `domain/usecase/AdjustInitialBalanceUseCase.kt` — idem
- `domain/usecase/SetDefaultAccountUseCase.kt` — `Either<Throwable, Unit>` com `either { catch { } }`
- `domain/usecase/AdjustInvoiceUseCase.kt` — idem
- `domain/usecase/DeleteCreditCardUseCase.kt` — idem
- `domain/usecase/CreateDefaultCategoriesUseCase.kt` — idem
- `domain/usecase/EnsureDefaultAccountUseCase.kt` — `Either<Throwable, Account>`
- `domain/usecase/CreateSupportIssueUseCase.kt` — substituir `runCatching` por `either { catch { } }`
- `domain/usecase/AddSupportReplyUseCase.kt` — idem

---

## Critério de aceite

**Validação manual:**
1. Compilação sem erros após refatoração.
2. Testes existentes continuam passando (`./gradlew allTests`).
3. Use cases afetados não lançam exceções não tratadas — todos os caminhos retornam Either.

**Revisão de código:**
- [ ] Todos os use cases que fazem I/O retornam `Either<Throwable, ...>` (ou `Either<XxxException, ...>`)
- [ ] Nenhum `runCatching` restante nos use cases — todos usam `either { catch { } }`
- [ ] Validações usam `ensure` / `ensureNotNull` / `raise` — não `throw`
- [ ] Use cases de validação pura (`ValidateXxx`) mantêm `Either<XxxError, ...>` inalterado
- [ ] Use cases puros (cálculos) mantêm retorno plain inalterado

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
