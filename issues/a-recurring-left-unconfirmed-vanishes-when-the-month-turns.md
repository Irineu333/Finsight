---
area: recurring
severity: medium
type: data
---

# Recorrência não confirmada some na virada do mês, e não há backlog que a traga de volta

## Cenário

**DADO** uma recorrência ativa do dia 10, pendente e não confirmada em junho
**QUANDO** o calendário vira para 01/07
**ENTÃO** o cartão de pendência some do dashboard, e o que reaparece em 10/07 é a ocorrência
de **julho** — junho fica sem ocorrência, sem transação e sem nenhum registro de que faltou
**DEVERIA** os ciclos vencidos e não tratados continuarem pendentes até serem confirmados
ou pulados

## Mecânica

`GetPendingRecurringUseCase` decide tudo sobre `today.yearMonth`: o conjunto de tratadas é
`occurrences.filter { it.yearMonth == today.yearMonth }`, e o corte de dia é
`today.yearMonth.effectiveDay(recurring.dayOfMonth) <= today.day`. Não existe consulta a
meses anteriores em lugar nenhum do caminho.

Não há outra porta: o único consumidor é o dashboard, e a única entrada para a confirmação é
o cartão de pendência — `RecurringScreen` lista *recorrências*, não ocorrências.

O contorno existe e é indireto: o seletor de data da confirmação não tem `minDate`, então dá
para retroagir a data até junho. O preço é que julho fica pendente no lugar — é o cenário de
`confirming-a-recurring-in-another-month-leaves-the-current-one-pending`.

## Evidência

- `feature/recurring/api/.../usecase/GetPendingRecurringUseCase.kt` — `invoke()`: as duas
  comparações contra `today.yearMonth`, e nenhuma leitura de mês anterior
- `feature/dashboard/impl/.../DashboardComponentsBuilder.kt` — `pendingRecurring()`, o único
  consumidor; `handledRecurringIds` filtra por `currentYearMonth` de novo
- `feature/recurring/impl/.../usecase/ConfirmRecurringUseCase.kt` — a ocorrência é gravada em
  `date.yearMonth`, o que torna junho alcançável apenas retroagindo a data
- `feature/recurring/impl/.../recurring/RecurringScreen.kt` — lista recorrências; não há tela
  de ocorrências pendentes

## Consequência

Uma despesa mensal recorrente deixa de ser lançada e o app para de lembrar, sem nenhum sinal.
Como a lista de pendentes esvazia sozinha, o usuário conclui que tratou o que na verdade
perdeu.

## Sugestão

Fazer a pendência varrer de `min(mês da primeira ocorrência esperada, mês corrente)` até hoje,
em vez de só o mês corrente. Fechar isso também obriga a decidir até onde atrás o backlog vai.
Não vinculante.
