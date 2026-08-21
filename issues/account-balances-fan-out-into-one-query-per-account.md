---
area: dashboard
severity: low
type: performance
---

# O saldo de cada conta é uma consulta, e não existe a consulta agrupada que evitaria isso

## Invariante

Uma leitura sobre N contas é uma consulta, não N.

Hoje é falso na visão geral de contas do dashboard: `accountsOverview()` chama
`entryRepository.balance(account.id)` **dentro** do `map` sobre todas as contas, uma consulta
por conta a cada reconstrução do dashboard.

## Mecânica

O razão já expõe a forma agrupada para a outra dimensão do problema — `totalsByDimension` e
`totalsByDimensionInScope` são um `GROUP BY` só —, mas não há equivalente por conta:
`IEntryRepository` declara `balance(accountId)` e `balanceUpToByCurrency(...)`, ambos por
conta única, e `EntryDao` não tem nenhum `GROUP BY accountId`.

Isso multiplica com o gatilho: o `combine` de nove fontes do `DashboardViewModel` reexecuta o
construtor inteiro a cada emissão de qualquer uma delas.

## Evidência

- `feature/dashboard/impl/.../DashboardComponentsBuilder.kt` — `accountsOverview()`:
  `entryRepository.balance(account.id)` dentro do `.map { account -> … }`
- `core/ledger/.../repository/IEntryRepository.kt` — `balance(accountId)`; nenhuma leitura de
  saldo por conjunto de contas
- `core/ledger/.../dao/EntryDao.kt` — `balanceOf(accountId)` e `balanceUpToDate(...)`, ambas
  por conta única; nenhum `GROUP BY accountId`
- o precedente na outra dimensão: `totalsByDimension()` / `totalsByDimensionInScope()`, e o
  bug irmão `dimension-balances-fan-out-into-one-query-per-dimension`

## Consequência

O custo de abrir o dashboard cresce linearmente com o número de contas, e volta a ser pago a
cada escrita no razão.

## Sugestão

Uma `balancesByAccount(accountIds)` no DAO — um `GROUP BY accountId` — e o construtor
resolvendo o mapa uma vez antes do `map`. Não vinculante.
