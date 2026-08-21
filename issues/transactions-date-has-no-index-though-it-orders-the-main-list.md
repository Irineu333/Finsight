---
area: ledger
severity: low
type: performance
---

# `transactions.date` não tem índice, embora ordene a lista principal

## Invariante

A coluna pela qual a lista principal ordena é indexada.

Hoje é falso: `TransactionEntity` declara índices para `installmentId`, `recurringId` e
`recurringCycle` — nenhum para `date`.

## Mecânica

`date` é o `ORDER BY date DESC, id DESC` de `observeAll()`, `getAll()` e `observeBy()`, e
entra como predicado em `EntryDao.scopeStats` (`o.date <= :endDate`) e
`totalsByDimensionWithSiblingLeg` (`o.date BETWEEN :start AND :end`). Sem índice, o SQLite
varre a tabela e ordena em memória a cada reemissão — que acontece a cada escrita no razão.

Vale registrar o limite do ganho, para não superestimá-lo: os agregados mensais usam
`substr(o.date, 1, 7) = :yearMonth`, que não é sargável e **não** se beneficiaria de um
índice simples.

## Evidência

- `core/ledger/.../entity/TransactionEntity.kt` — `indices = [Index("installmentId"),
  Index("recurringId"), Index("recurringCycle")]`
- `core/database/schemas/com.neoutils.finsight.database.AppDatabase/14.json` — a entidade
  `transactions`, com os mesmos três
- `core/ledger/.../dao/TransactionDao.kt` — `observeAll()` e `observeBy()`, com o `ORDER BY`
- `core/ledger/.../dao/EntryDao.kt` — `scopeStats()` e `totalsByDimensionWithSiblingLeg()`

## Consequência

Ordenação O(n log n) repetida, que se combina com
`the-ledger-is-re-read-whole-on-every-reactive-transaction-read` para multiplicar o custo
por tela assinada.

## Sugestão

`Index(value = ["date"])` — ou `["date", "id"]`, casando com o `ORDER BY` — mais a
migração. Não vinculante.

*Índices verificados no código e no schema exportado; `EXPLAIN QUERY PLAN` não foi rodado.*
