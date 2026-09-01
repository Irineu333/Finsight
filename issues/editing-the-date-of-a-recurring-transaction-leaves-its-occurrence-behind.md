---
area: transactions
severity: medium
type: data
---

# Editar a data de uma transação de série não move a ocorrência que a registra

## Invariante

A `RecurringOccurrence` que aponta para uma transação está arquivada no mês em que essa
transação cai.

Hoje é falso: editar a data da transação para outro mês não move a ocorrência.

Observável: a transação do ciclo de setembro é editada para outubro — a edição é oferecida,
porque só parcelamento e ajuste são bloqueados. A ocorrência continua dizendo setembro.
Setembro fica marcado como tratado sem ter transação, outubro volta a aparecer como
pendente, e uma segunda confirmação é aceita.

## Mecânica

`updateTransaction` reescreve linha e pernas preservando `recurringId` e `recurringCycle` —
o `@Query` do DAO só toca `title` e `date` —, mas nada em nenhuma camada atualiza
`recurring_occurrences`.

Não há dono para isso: `RecurringOccurrenceDao` não expõe consulta por `transactionId`, e o
razão só oferece a porta de **remoção** (`TransactionRemovalHook`). A remoção está coberta
pelo `ON DELETE CASCADE` da FK; a **edição** não tem porta nem gancho.

O portão de edição enumera as condições uma a uma e nenhuma delas menciona `recurringId`.

## Evidência

- `feature/transactions/impl/.../viewTransaction/ViewTransactionUiState.kt` —
  `Content.isEditable`: `label != ADJUSTMENT && monetaryEntries.size == 1 &&
  installmentId == null && isChangeable`
- `feature/transactions/impl/.../editTransaction/EditTransactionViewModel.kt` — `submit()`
  chama `updateTransaction(...)` e nada mais
- `core/ledger/.../dao/TransactionDao.kt` — `update()`: `SET title = :title, date = :date`
- `core/database/.../dao/RecurringOccurrenceDao.kt` — as consultas existentes; nenhuma por
  `transactionId`
- `core/ledger/.../ledger/TransactionRemovalHook.kt` — a única porta; não há equivalente
  para atualização
- `feature/transactions/impl/.../editTransaction/EditTransactionModal.kt` — a data é
  editável por texto e por calendário

## Consequência

Mês marcado como tratado sem lançamento, mês com lançamento sem ocorrência, e um convite a
lançar a mesma despesa mensal de novo.

## Sugestão

Ou uma porta análoga ao `TransactionRemovalHook` para atualização, ou reprojetar a
ocorrência no `submit()` quando `transaction.recurringId != null`. A primeira é mais cara e
mantém a regra do lado de quem é dono dela. Não vinculante.
