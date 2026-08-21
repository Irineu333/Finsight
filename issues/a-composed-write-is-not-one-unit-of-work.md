---
area: transversal
severity: medium
type: data
---

# Uma escrita composta não é uma unidade de trabalho

## Invariante

Uma operação que faz N escritas para produzir **um** resultado ou acontece inteira, ou não
acontece.

Hoje é falso em três lugares, todos com o padrão correto disponível no mesmo repositório:
`useWriterConnection { immediateTransaction { … } }`, que `CategoryRepository`,
`TransactionRepository` e `RecurringOccurrenceRepository` já usam.

## Mecânica

Cada um monta o resultado em passos independentes, e uma falha ou cancelamento entre eles
deixa um estado que nenhum caminho de leitura sabe interpretar.

- **`BudgetRepository.update()`** faz `dao.update` → `deleteBudgetCategories` → N
  `insertBudgetCategory`, tudo solto — a classe sequer recebe o `AppDatabase`. Falhar entre o
  delete e os inserts deixa o orçamento **sem nenhuma categoria**, e um orçamento sem
  categorias reporta gasto zero para sempre, sem sinal de erro. `insert()` tem a mesma forma.
- **`AddInstallmentUseCaseImpl`** cria cada fatura faltante em sua própria transação
  (`getInvoices()`), e o rollback do fan-out desfaz **só** o installment
  (`onLeft { installmentRepository.deleteInstallmentById(...) }`). Parcelar em 12x num cartão
  com 3 faturas cria 9 `FUTURE`; se `createTransactions` falhar, as 9 ficam lá, vazias, para o
  usuário apagar à mão.
- **`SetDefaultAccountUseCase`** percorre as contas emitindo um `update` por vez. Entre o
  update que desmarca a antiga e o que marca a nova existe uma emissão com **zero** contas
  padrão, e uma falha ali a torna permanente.

## Evidência

- `core/database/.../repository/BudgetRepository.kt` — `insert()` e `update()`, sem transação;
  o construtor recebe `dao`, `mapper` e `categoryRepository`, e nenhum `AppDatabase`
- `feature/creditcards/impl/.../usecase/AddInstallmentUseCaseImpl.kt` — `getInvoices()` e o
  `catch { … }.onLeft { deleteInstallmentById(...) }` de `registerTransactions()`
- `feature/accounts/impl/.../usecase/SetDefaultAccountUseCase.kt` — o `accounts.forEach { … }`
  com um `repository.update(...)` por iteração
- o padrão correto: `core/ledger/.../repository/TransactionRepository.kt`
  (`deleteTransactionsByIds`) e
  `feature/recurring/impl/.../repository/RecurringOccurrenceRepository.kt` (`confirmCycle`),
  cujo KDoc explica por que leitura e escritas ficam na mesma unidade

## Consequência

Um orçamento que reporta zero para sempre, faturas vazias que o usuário tem de limpar, e uma
janela em que o app não tem conta padrão — que os chamadores de `getDefaultAccount()` não
esperam.

## Sugestão

Envolver as três em `useWriterConnection { immediateTransaction { … } }`. Para o
`BudgetRepository` isso significa receber o `AppDatabase`, como os demais repositórios já
recebem. Não vinculante.
