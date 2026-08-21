---
area: recurring
severity: medium
type: data
---

# Confirmar recorrência com data de outro mês arquiva lá e deixa o mês corrente pendente

## Cenário

**DADO** uma recorrência ativa e pendente no mês corrente
**QUANDO** o usuário abre a confirmação, muda a data para um dia do mês anterior — o
seletor permite qualquer data passada — e confirma
**ENTÃO** a ocorrência é gravada no mês da data escolhida, o cartão de pendência continua
na dashboard porque o mês corrente segue sem ocorrência, e uma segunda confirmação é aceita
sem qualquer recusa
**DEVERIA** ou avisar que a confirmação foi arquivada em outro mês, ou impedir que a data
saia do mês pendente

## Mecânica

O mês da ocorrência vem da data escolhida (`date.yearMonth`), enquanto a pendência é
decidida só sobre `today.yearMonth`. A recusa de reentrada é por `(recurringId, yearMonth)`,
então meses diferentes nunca colidem — e a mesma despesa mensal é gravada duas vezes.

`DatePickerModal` aceita `minDate`; o modal de confirmação passa só `maxDate`.

## Evidência

- `feature/recurring/api/.../usecase/GetPendingRecurringUseCase.kt` — `invoke()`:
  `occurrences.filter { it.yearMonth == today.yearMonth }`
- `feature/recurring/impl/.../usecase/ConfirmRecurringUseCase.kt` — `invoke()`:
  `val yearMonth = date.yearMonth`, usado na ocorrência e no `cycleNumber`
- `feature/recurring/impl/.../repository/RecurringOccurrenceRepository.kt` —
  `confirmCycle()`: a recusa é por `getByRecurringAndMonth(recurringId, yearMonth)`
- `feature/recurring/impl/.../confirmRecurring/ConfirmRecurringModal.kt` — o
  `DatePickerModal` recebe `maxDate` e não `minDate`
- `core/designsystem/.../modal/date/DatePickerModal.kt` — `selectableDates` suporta
  `minDate`
- `feature/dashboard/impl/.../DashboardComponentContent.kt` —
  `DashboardPendingRecurringSection`, o cartão que não some

## Consequência

Despesa mensal lançada em duplicidade, com saldo e orçamento errados, e um mês marcado como
tratado que a lista de pendentes nunca mais consulta.

## Sugestão

Limitar o `DatePickerModal` da confirmação ao mês pendente, ou fazer a pendência considerar
o mês da data confirmada. A primeira também fecha
`a-recurring-cycle-can-be-numbered-zero-or-below`. Não vinculante.
