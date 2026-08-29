---
area: recurring
severity: high
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

O contorno que existia foi fechado. `confirmableDates(cycleMonth, today)` limita a confirmação
ao mês do ciclo que a abriu — `first..maxOf(first, minOf(today, cycleMonth.lastDay))` — e a
janela é aplicada nos cinco pontos do view model, com o campo de data `readOnly` e o
`DatePickerModal` recebendo `minDate`/`maxDate` dela. Retroagir a data para o mês perdido deixou
de ser possível.

O redesenho da tela tornou a perda **visível sem torná-la resolvível**: com o seletor de mês, um
mês passado exibe a seção "Pendente" com a sua contagem — todo ciclo sem ocorrência de um mês
encerrado é `PENDING`, porque `date <= today` —, e a linha leva a `ViewRecurringModal`, que não
oferece confirmar nem pular. A tela afirma uma obrigação em aberto e nenhuma ação do app a
responde.

## Evidência

- `feature/recurring/api/.../usecase/GetPendingRecurringUseCase.kt` — `invoke()`: as duas
  comparações contra `today.yearMonth`, e nenhuma leitura de mês anterior
- `feature/dashboard/impl/.../DashboardComponentsBuilder.kt` — `pendingRecurring()`, o único
  consumidor; `handledRecurringIds` filtra por `currentYearMonth` de novo
- `feature/recurring/impl/.../usecase/ConfirmRecurringUseCase.kt` — a ocorrência é gravada em
  `date.yearMonth`, o que torna junho alcançável apenas retroagindo a data
- `feature/recurring/impl/.../modal/confirmRecurring/ConfirmRecurringViewModel.kt` —
  `confirmableDates()`, e os cinco `coerceIn(confirmableDates)`
- `feature/recurring/impl/.../modal/confirmRecurring/ConfirmRecurringModal.kt` — campo de data
  `readOnly = true`; `DatePickerModal(minDate = uiState.confirmableDates.start, maxDate = …endInclusive)`
- `feature/recurring/api/.../usecase/GetRecurringCyclesUseCase.kt` — `invoke()`:
  `if (date <= today) PENDING else UPCOMING`
- `feature/recurring/impl/.../recurring/RecurringScreen.kt` — o `onClick` da linha mostra
  `ViewRecurringModal`; `SectionHeader` publica `recurring_section_pending_count`
- `feature/recurring/impl/.../recurring/RecurringAction.kt` — só `SelectFilter` e `SelectMonth`:
  nenhuma ação de confirmar ou pular
- `feature/dashboard/impl/.../dashboard/DashboardComponentContent.kt` — `confirmRecurringModal`
  tem um único chamador no app inteiro, e ele calcula `targetDate` sobre `currentDate.yearMonth`

## Consequência

Uma despesa mensal recorrente deixa de ser lançada e o app para de lembrar, sem nenhum sinal.
Como a lista de pendentes esvazia sozinha, o usuário conclui que tratou o que na verdade
perdeu.

## Sugestão

Fazer a pendência varrer de `min(mês da primeira ocorrência esperada, mês corrente)` até hoje,
em vez de só o mês corrente. Fechar isso também obriga a decidir até onde atrás o backlog vai.
Não vinculante.
