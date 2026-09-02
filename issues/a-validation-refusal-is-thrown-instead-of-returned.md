---
area: transversal
severity: low
type: data
---

# Uma recusa de validação é lançada em vez de devolvida

## Invariante

O tipo devolvido por um use case nomeia as recusas que ele pode dar.

Hoje é falso: **45 assinaturas de produção devolvem `Either<Throwable, _>`**. Para a maioria
delas o `Throwable` é honesto — embrulham I/O, e o que pode dar errado é aberto. Em dois
casos não é: o use case **tem** o erro tipado na mão e o embrulha numa exceção só para
caber na assinatura.

## Mecânica

`BuildTransactionUseCaseImpl` recebe de `validateTransactionForm(form)` um
`Either<BuildTransactionError, LocalDate>` — o erro já tipado, já exaustivo — e faz
`.mapLeft { BuildTransactionException(it) }`. `SaveRecurringUseCase` faz o mesmo com
`RecurringForm.toRecurring()`: `.mapLeft { RecurringException(it) }`.

O tipo que sai é `Throwable`, e o chamador tem de reabri-lo com `is XxxException` para saber
o que já se sabia uma linha antes. É esse apagamento que torna possíveis os dois defeitos
irmãos: gravar validação no Crashlytics
(*an-expected-refusal-is-recorded-in-crashlytics-as-a-failure*) e cair no genérico
(*a-refusal-with-its-own-message-still-arrives-as-the-generic-one*). Nenhum dos dois se
fecha por aqui — mas os dois ficam mais fáceis de reabrir enquanto isto valer.

A convenção do projeto já decide isto: `CLAUDE.md` reserva o embrulho `XxxException` para
"operation use cases that can throw" e manda o de validação devolver o tipo do erro pelo
`Either`. Os dois casos acima são de validação. A forma certa também existe nos dois lados do
embrulho, o que torna o desvio local e não estrutural:
`ValidateTransactionFormUseCase` devolve `Either<BuildTransactionError, LocalDate>` — é o
próprio argumento que `BuildTransactionUseCaseImpl` converte — e
`ValidateAccountNameUseCase` devolve `Either<AccountError, String>`.

## Evidência

- `feature/transactions/impl/.../usecase/BuildTransactionUseCaseImpl.kt` — o
  `.mapLeft { BuildTransactionException(it) }` e os três `ensureNotNull` que também
  embrulham `BuildTransactionError`
- `feature/transactions/api/.../usecase/BuildTransactionUseCase.kt` — a assinatura
  `Either<Throwable, TransactionIntent>`
- `feature/recurring/impl/.../usecase/SaveRecurringUseCase.kt` — o
  `.mapLeft { RecurringException(it) }`, com `Either<Throwable, Unit>`
- padrão certo: `feature/transactions/api/.../usecase/ValidateTransactionFormUseCase.kt` e
  `feature/accounts/impl/.../usecase/ValidateAccountNameUseCase.kt`
- `grep -rn "Either<Throwable"` em produção — 45 assinaturas, o contexto em que as duas
  acima passam despercebidas
- `CLAUDE.md` §Error Types — a convenção que separa use case de operação de use case de
  validação

## Consequência

Nenhuma para quem usa o app. O que se perde é a única checagem barata que havia: com o erro
no tipo, esquecer um caso é erro de compilação; com `Throwable`, é um `else` silencioso.

## Sugestão

Devolver o enum: `Either<BuildTransactionError, TransactionIntent>` e
`Either<RecurringError, Unit>`. A parte de I/O de cada um continua podendo falhar por fora e
precisa de um tipo soma — é isso que faz a mudança não ser trivial, e é a decisão que quem
corrigir tem de tomar. As 45 assinaturas restantes não estão sob acusação aqui. Não
vinculante.
