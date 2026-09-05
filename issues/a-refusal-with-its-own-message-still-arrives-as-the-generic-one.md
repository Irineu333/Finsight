---
area: transversal
severity: medium
type: ux
---

# Uma recusa que tem mensagem própria chega ao usuário como "algo deu errado"

## Invariante

A causa de uma recusa chega ao usuário nos termos dela.

Hoje é falso, de três formas distintas: o tipo do erro não tem ramo próprio, o ViewModel não
pergunta, ou os dois lados existem e ninguém os ligou. Nos três casos a tela diz *"Algo deu
errado. Tente de novo em instantes."* para uma recusa determinística, que vai recusar de
novo.

Observável: o usuário tenta pagar uma fatura com valor maior que o devido. O domínio recusa
com `InvoiceError.AmountExceedsInvoice` — que sabe exatamente o que houve — e a tela oferece
"tente de novo em instantes".

## Mecânica

**1. O erro não tem ramo.** `InvoiceError.toUiText()` nomeia 3 dos 27 casos e fecha com
`else -> ledger_action_error_generic`. O `when` não é exaustivo, então um caso novo entra no
genérico sem que o compilador diga nada — e é o **único `toUiText()` do projeto que fecha com
`else`**. Entre os 24 no genérico estão as causas mais acionáveis que o app tem:
`CannotCloseOutsideClosingMonth`, `NegativeBalance`, `AmountExceedsInvoice`,
`PaymentDateAfterDue`, `InvoiceNotInDebt`.

**2. O ViewModel não pergunta.** `ConfirmRecurringViewModel.confirm()` monta um
`UiText.Res(retire_action_error_generic)` fixo, sem olhar o `it`. Entre o que ele descarta
está `RecurringException(RecurringError.CURRENCY_MISMATCH)`, lançado por
`rejectIfCurrencyDiffers` e traduzido por `RecurringError.toUiText()` em
`recurring_error_currency_mismatch` — a mensagem existe, escrita nos dois idiomas, e a tela
não a pede.

E não é um caso: `RecurringError.toUiText()` é exaustivo em **oito** membros, com as oito
chaves nos dois idiomas, e **nenhum consumidor** — `grep -rn "toUiText" feature/recurring/`
não devolve nada. `SkipRecurringViewModel.skip()` monta o mesmo genérico fixo, e
`SkipRecurringUseCaseImpl` lança `RecurringException(RecurringError.NOT_FOUND)`, que tem
tradução própria. A feature consome um mapper — `toRecurringRetireUiMessage()`, nas modais de
arquivar e apagar — e é o de outro domínio de erro. O efeito é que cada correção que
acrescenta uma recusa a este domínio escreve uma mensagem que ninguém vai ler, e passa na
revisão porque a convenção de Error Types está cumprida.

**3. Os dois lados existem e ninguém os ligou.** `BuildTransactionError.toUiText()` é
exaustivo, com 13 ramos e 13 chaves em pt e en. **Nenhum chamador em produção.** Os dois
`toUiMessage()` que precisariam dele — `AddTransactionViewModel` e `EditTransactionViewModel`
— não têm o ramo `is BuildTransactionException`, e caem em `transaction_error_generic`.

*Hipótese, não verificada: `canSubmit` é estado derivado e "chega um frame depois" (comentário
do próprio `AddTransactionViewModel.submit()`), então um submit logo após digitar pode passar
pela porta com a resposta velha e produzir o `BuildTransactionException` que ninguém traduz.*

## Evidência

- `core/model/.../domain/error/InvoiceError.kt` — `toUiText()`, o `else ->` e os 27
  subtipos acima dele
- `feature/creditcards/impl/.../payInvoice/PayInvoiceViewModel.kt` e
  `.../advancePayment/AdvancePaymentViewModel.kt` — os dois `toUiMessage()` que traduzem
  `InvoiceException` corretamente e ainda assim exibem o genérico, porque é o que
  `InvoiceError.toUiText()` devolve; é o caminho do cenário acima
- os outros 14 `toUiText()` do projeto, todos exaustivos; os dois `else` que existem em
  `RetireError.kt` e `RecurringRetireError.kt` estão num `Throwable.toXxxUiMessage()`, onde
  são inevitáveis
- `feature/recurring/impl/.../confirmRecurring/ConfirmRecurringViewModel.kt` —
  `confirm()`, o `showError(UiText.Res(Res.string.retire_action_error_generic))`
- `feature/recurring/impl/.../usecase/ConfirmRecurringUseCase.kt` —
  `rejectIfCurrencyDiffers()`, que lança o erro traduzível descartado acima
- `core/model/.../domain/error/BuildTransactionError.kt` — `toUiText()`, 13 ramos
- `grep -rn "build_transaction_error_"` fora do enum — nenhuma ocorrência
- `feature/transactions/impl/.../addTransaction/AddTransactionViewModel.kt` e
  `.../editTransaction/EditTransactionViewModel.kt` — `toUiMessage()`, sem
  `is BuildTransactionException`
- padrão certo, para comparação: `DeleteAccountViewModel.toUiMessage()`,
  `ReopenInvoiceViewModel.toUiMessage()`, `AddInstallmentViewModel.submit()` — os três
  perguntam ao erro
- `feature/recurring/impl/.../skipRecurring/SkipRecurringViewModel.kt` — `skip()`, o mesmo
  genérico fixo; e `SkipRecurringUseCaseImpl`, que lança
  `RecurringException(RecurringError.NOT_FOUND)` antes dos dois `require`
- `core/model/.../domain/error/RecurringError.kt` — oito membros, `toUiText()` exaustivo,
  `grep -rn "toUiText" feature/recurring/` vazio

## Consequência

"Tente de novo em instantes" é falso para toda recusa desta lista: a condição não passa com
o tempo. O usuário repete a ação, obtém o mesmo nada, e não descobre o passo que faltava —
mudar a data, reduzir o valor, ajustar o saldo da fatura antes. As 26 traduções de
`BuildTransactionError` e as 16 de `RecurringError` são trabalho pago e não entregue.

## Sugestão

São três correções independentes, não uma. A mais barata é (3): dois ramos `when`. A de
maior alcance é (1): trocar o `else` por ramos exaustivos obriga o compilador a cobrar cada
caso novo. Vale notar que isto é irmão de
*a-refused-write-says-nothing-to-whoever-asked-for-it* — lá a tela não diz nada, aqui diz a
coisa errada. Não vinculante.
