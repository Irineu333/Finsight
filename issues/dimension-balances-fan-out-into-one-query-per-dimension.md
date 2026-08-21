---
area: ledger
severity: low
type: performance
---

# O saldo por dimensão é um leque de N consultas onde a consulta agrupada já existe

## Invariante

Uma leitura que o README lista entre as respostas "por moeda", ao lado de irmãs que
prometem explicitamente *"numa consulta"*, é uma consulta.

Hoje é falso para `dimensionBalancesInMonthByCurrency`: ela emite uma consulta por dimensão.

## Mecânica

A extensão faz `dimensionIds.distinct().associateWith { dimensionBalanceInMonthByCurrency(month, it) }`
— N chamadas suspensas ao DAO, recomputadas a cada emissão de `observeConsolidationChanges()`.

O `EntryDao` já tem duas consultas agrupadas por `(dimensionId, currency)` que resolveriam
isso numa ida só: `totalsByDimensionInMonth(nominalType, yearMonth)` e
`naturalBalanceByDimension(ids)`. O próprio KDoc da extensão admite ser *"a thin fan
over…"*.

## Evidência

- `core/ledger/.../repository/IEntryRepository.kt` — a extensão
  `dimensionBalancesInMonthByCurrency()`, no fim do arquivo
- `core/ledger/.../dao/EntryDao.kt` — `totalsByDimensionInMonth()` e
  `naturalBalanceByDimension()`, as versões agrupadas
- `feature/budgets/api/.../usecase/CalculateBudgetProgressUseCase.kt` — o único chamador,
  passando uma dimensão por categoria de cada orçamento
- `core/ledger/README.md` — a tabela "Por moeda", linha "O mesmo, para várias dimensões"

## Consequência

N consultas por recomputação de orçamentos, numa tela que recomputa a cada escrita no razão
e a cada taxa cadastrada. Nenhum impacto de correção — os números estão certos.

## Sugestão

Um membro apoiado numa consulta agrupada por `(dimensionId, currency)` filtrada pelas ids,
no mesmo molde de `naturalBalanceByDimension`. Não vinculante.

*Leitura de código; não medido.*
