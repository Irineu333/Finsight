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

`date` é o `ORDER BY date DESC, id DESC` de `observeAll()`, `getAll()`, `getBetween()` e
`observeBy()`, é o `WHERE date BETWEEN` do primeiro, e entra como predicado em
`EntryDao.scopeStats` (`o.date <= :endDate`) e
`totalsByDimensionWithSiblingLeg` (`o.date BETWEEN :start AND :end`). Sem índice, o SQLite
varre a tabela e ordena em memória a cada reemissão — que acontece a cada escrita no razão.

Vale registrar o limite do ganho, para não superestimá-lo: os agregados mensais usam
`substr(o.date, 1, 7) = :yearMonth`, que não é sargável e **não** se beneficiaria de um
índice simples.

## Evidência

- `core/ledger/.../entity/TransactionEntity.kt` — `indices = [Index("installmentId"),
  Index("recurringId"), Index("recurringCycle")]`
- `core/database/schemas/com.neoutils.finsight.database.AppDatabase/15.json` — a entidade
  `transactions` no schema corrente (`AppSchema.VERSION` = 15), com os mesmos três
- `core/ledger/.../dao/TransactionDao.kt` — `observeAll()`, `observeBy()` e `getBetween()`,
  esta última com o `WHERE date BETWEEN :startDate AND :endDate` e o mesmo `ORDER BY`
- `core/ledger/.../dao/EntryDao.kt` — `scopeStats()` e `totalsByDimensionWithSiblingLeg()`

## O plano de consulta, medido

`EXPLAIN QUERY PLAN` contra uma base construída a partir do `createSql` de `15.json` — a tabela e
os três índices que ela declara:

```
getBetween                      -> SCAN transactions
                                   USE TEMP B-TREE FOR ORDER BY
observeAll / getAll             -> SCAN transactions
                                   USE TEMP B-TREE FOR ORDER BY

com CREATE INDEX (date, id):
getBetween                      -> SEARCH transactions USING INDEX (date>? AND date<?)
observeAll / getAll             -> SCAN transactions USING INDEX      (sem b-tree temporária)
```

O índice composto resolve as duas metades: o corte do mês vira `SEARCH`, e a ordenação passa a ser
servida pelo próprio índice em vez de uma b-tree em memória.

Nenhum teste vê isso: `TransactionListingCostTest` conta postagens hidratadas pela porta do
repositório, e linhas varridas pelo SQLite não passam por ali.

## Consequência

Ordenação O(n log n) repetida, que se combina com
`the-ledger-is-re-read-whole-on-every-reactive-transaction-read` para multiplicar o custo
por tela assinada.

## Sugestão

`Index(value = ["date"])` — ou `["date", "id"]`, casando com o `ORDER BY` — mais a
migração. A medição acima diz qual dos dois: só o composto dispensa também a b-tree
temporária. Não vinculante.
