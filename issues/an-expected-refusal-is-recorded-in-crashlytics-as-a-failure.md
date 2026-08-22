---
area: transversal
severity: low
type: data
---

# Uma recusa esperada é gravada no Crashlytics como se fosse falha

## Invariante

Só o inesperado chega ao Crashlytics.

Hoje é falso: há **41 chamadas de `crashlytics.recordException(it)` em 37 ViewModels** de
produção, e nenhuma olha o que `it` é. A recusa que o domínio existe para dar — "esta conta
ainda tem saldo", "só a última fatura fechada pode ser reaberta" — sobe para o painel de
falhas pelo mesmo caminho da exceção de banco que ninguém previu.

O mesmo `onLeft` frequentemente prova que a recusa era esperada: grava no Crashlytics e, na
linha seguinte, mostra ao usuário a mensagem própria dela.

## Mecânica

Nada no tipo separa os dois casos. O `Left` desses use cases é `Throwable` (ver
*a-validation-refusal-is-thrown-instead-of-returned*), então "isto é validação" só existe
depois de um `when (this) { is XxxException -> … }` — e esse `when` foi escrito para
traduzir a mensagem, não para decidir o que registrar.

`EditInvoiceBalanceViewModel.submit()` é o único sítio que exclui alguma coisa do registro,
e exclui um caso em que **nada falhou** (`InvoiceNotAdjustedException`, alvo igual ao saldo
atual). A discriminação cabe onde está; é a régua que não existe.

## Evidência

- `DeleteAccountViewModel.deleteAccount()` — `recordException(it)` e, logo abaixo,
  `showError(it.toUiMessage())`, cujo `toUiMessage()` traduz `AccountException`:
  `HAS_TRANSACTIONS` e `HAS_BALANCE` são gravados como falha
- `ArchiveAccountViewModel.archiveAccount()` — mesma dupla, mesmo `toUiMessage()`
- `ReopenInvoiceViewModel.reopenInvoice()` — grava `InvoiceException` antes de traduzi-la
- `AddInstallmentViewModel.submit()` — grava `InstallmentException` antes de traduzi-la
- `AddTransactionViewModel.submit()` e `EditTransactionViewModel.submit()` — idem, para
  `InvoiceException` / `ClosedAccountException` / `UnbalancedTransactionException`
- `ConfirmRecurringViewModel.confirm()` — grava tudo, inclusive
  `RecurringException(CURRENCY_MISMATCH)`
- contraste parcial: `EditInvoiceBalanceViewModel.submit()` — o único `when` antes do
  `recordException`
- `grep -rc "crashlytics.recordException(it)"` nos `*ViewModel.kt` de produção — 41 em
  37 arquivos, nenhuma condicional

## Consequência

O painel de falhas mede o uso normal do app, não os defeitos dele. Toda conta que alguém
tentou excluir com transações, toda fatura que alguém tentou reabrir fora de ordem, viram
volume — e o crash de verdade fica no meio disso. A régua da §SEVERITY não alcança isto
porque o custo não é do usuário: é de quem depende do painel para achar o que o usuário
sofreu.

## Sugestão

A régua não existe escrita em lugar nenhum: `CLAUDE.md` diz como o erro se declara
(`message` inglês, `toUiText()`, quando cabe uma exceção) e nada sobre o que se registra —
`grep -i crashlytics` em `CLAUDE.md`, `AGENTS.md` e `feature/README.md` só encontra o módulo.
Então são duas coisas, e a ordem importa: dizer a régua, e depois dar a ela um lugar só onde
seja aplicada. Hoje cada `onLeft` decide por conta própria, e 41 decisões independentes não
convergem. Não vinculante.
