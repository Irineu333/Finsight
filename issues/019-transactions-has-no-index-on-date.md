# 019 — a leitura por mês varre a tabela inteira: não há índice em `transactions.date`

**Área:** ledger · **Tipo:** performance · **Criticidade:** baixa · **Status:** aberto
**Verificado em:** 2026-08-18, por uma revisão adversarial do commit `87246cbe9`

## O que está errado

A [008](archive/008-list-transactions-loads-the-whole-table.md) desceu o corte do mês para o SQL, o
que eliminou a materialização de todo o ledger e o N+1 de entries — o custo dominante. Mas a leitura
nova ainda **varre e ordena a tabela inteira**, porque `transactions` não tem índice em `date`.

`EXPLAIN QUERY PLAN` contra o schema real (`AppDatabase/15.json`):

```
getBetween          -> SCAN transactions + USE TEMP B-TREE FOR ORDER BY
getExistingIds      -> SEARCH transactions USING INTEGER PRIMARY KEY (rowid=?)
getByTransactionIds -> SEARCH entries USING INDEX index_entries_transactionId
```

`TransactionEntity.kt:23-27` indexa `installmentId`, `recurringId` e `recurringCycle`. Nenhum em
`date`.

## Não é regressão

O `getAll()` anterior varria **e** materializava tudo, mais N queries. A correção é uma melhora real.
O que não procede é a afirmação de que o custo passou a ser constante no tamanho do ledger: a
hidratação passou, a varredura não.

Quem exagerou foi a nota arquivada da 008, e ela foi corrigida. O commit `87246cbe9` **não** faz essa
afirmação — ele diz que a mesma pergunta custa "6 hydrated postings on either ledger", que é escopado
à hidratação e procede. Uma mensagem de commit é história imutável de qualquer forma: o que se
corrige é o registro que continua sendo lido.

## Por que o teste não vê

`TransactionListingCostTest` conta postagens hidratadas pela porta do repositório. Linhas varridas
pelo SQLite não passam por ali.

## Correção sugerida

Índice em `transactions.date` — o que exige migração do Room e bump de schema, por isso está
registrado em vez de feito de carona.

Um teste que afirme o plano de consulta (`EXPLAIN QUERY PLAN` devolvendo `SEARCH` e não `SCAN`) é o
que fecharia a lacuna de medição, e é o mesmo idioma do `LedgerFixture`.
