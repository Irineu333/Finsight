---
area: recurring
severity: medium
type: data
---

# O número do ciclo de uma recorrência pode ser 0 ou negativo, e vai para a tela

## Cenário

**DADO** uma recorrência criada hoje, cujo ciclo 1 é o mês corrente
**QUANDO** o usuário confirma — ou pula — um ciclo escolhendo uma data de um mês anterior
**ENTÃO** grava-se `recurringCycle` = 0 para o mês anterior, −1 para dois meses antes, e o
detalhe da transação exibe `"Aluguel • 0"`
**DEVERIA** ou recusar a data anterior à origem da série, ou não numerar ciclos abaixo de 1

## Mecânica

`cycleNumber` é `createdAt.toYearMonth().monthsUntil(yearMonth) + 1`, e `monthsUntil` é uma
subtração sem piso: `(other.year - year) * 12 + (other.month.number - month.number)`.
Nada valida o resultado antes de persistir, e `TransactionRecurring.label` concatena o
número cru.

Os dois caminhos que numeram — confirmar e pular — repetem o mesmo cálculo e a mesma
ausência de piso.

## Evidência

- `feature/recurring/impl/.../usecase/ConfirmRecurringUseCase.kt` — `invoke()`, o cálculo
  de `cycleNumber`
- `feature/recurring/impl/.../usecase/SkipRecurringUseCase.kt` — mesmo cálculo, mesma
  ausência de piso
- `core/common/.../extension/YearMonth.kt` — `YearMonth.monthsUntil()`, resultado negativo
  permitido
- `core/model/.../model/TransactionRecurring.kt` — `label = "${instance.label} • $cycleNumber"`
- `feature/transactions/impl/.../viewTransaction/ViewTransactionModal.kt` — o `DetailRow`
  que renderiza `recurring.label`

## Consequência

Rótulo sem sentido no detalhe da transação, e uma numeração de ciclos que deixa de ser um
ordinal — o que compromete qualquer leitura futura por número de ciclo, inclusive a busca
por `(recurringId, cycleNumber)`.

## Sugestão

O `minDate` proposto em
`confirming-a-recurring-in-another-month-leaves-the-current-one-pending` resolve os dois de
uma vez; alternativamente, recusar `cycleNumber < 1` nos dois casos de uso. Não vinculante.
