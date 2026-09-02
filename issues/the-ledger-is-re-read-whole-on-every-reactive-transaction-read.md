---
area: ledger
severity: low
type: performance
---

# Toda leitura reativa de transação relê a tabela `entries` inteira

## Invariante

Uma leitura escopada a uma transação lê as pernas daquela transação.

Hoje é falso: abrir o detalhe de **uma** transação carrega o razão completo, e cada
assinatura ativa remapeia todas as transações a cada escrita.

## Mecânica

`TransactionRepository.mapToDomain()` combina `transactionDao.observeAll()` com
`entryDao.observeAll()` — `SELECT * FROM entries ORDER BY id ASC`, sem filtro.
`observeTransactionById(id)` é construído **sobre** `observeAllTransactions()` e só depois
filtra em memória; o `distinctUntilChanged()` vem *depois* do mapeamento, então não poupa
trabalho nenhum.

O caminho barato já existe, escrito e indexado — e sem um único chamador de produção:
`TransactionDao.observeById(id)` e
`EntryDao.observeEntriesWithAccountByTransactionId(id)`, este último com `@Transaction` +
`@Relation` sobre `index_entries_transactionId`. Os dois métodos correspondentes de
`IEntryRepository` também estão implementados e só aparecem em fakes de teste.

## Evidência

- `core/ledger/.../repository/TransactionRepository.kt` — `mapToDomain()`:
  `flowCombine(this, accountsFlow, entryDao.observeAll())`
- mesmo arquivo — `observeTransactionById()`:
  `observeAllTransactions().map { it.firstOrNull { it.id == id } }.distinctUntilChanged()`
- `core/ledger/.../dao/TransactionDao.kt` — `observeById()`, declarado e sem chamador
- `core/ledger/.../dao/EntryDao.kt` — `observeEntriesWithAccountByTransactionId()`, idem
- `core/ledger/.../repository/EntryRepository.kt` — `getEntriesByTransaction()` e
  `observeEntriesByTransaction()`: implementados, e só aparecem em fakes de teste
- assinantes de `observeAllTransactions()`: `TransactionsViewModel`, `DashboardViewModel`,
  `AccountsViewModel`, `BudgetsViewModel`, `InstallmentsViewModel`

## Consequência

Custo de CPU e alocação da ordem do razão inteiro, por escrita e por tela assinada. Cresce
linearmente com a vida do usuário, e piora exatamente onde ele mais usa o app.

## Sugestão

Reconstruir `observeTransactionById` sobre as duas consultas que já existem. Para as listas,
avaliar um `observeEntriesForTransactionIds`. Não vinculante.

*Leitura de código; o tempo não foi medido em aparelho real.*
