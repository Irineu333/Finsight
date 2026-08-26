---
area: recurring
severity: medium
type: data
verdict: fixed
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

## Desfecho

**Causa real** — a sugestão oferecia o `minDate` do bug irmão, e ele de fato fecha o
caminho; mas fechar o caminho não é a mesma coisa que dar à regra um dono. A numeração
estava escrita à mão nos dois casos de uso que gravam, com a mesma fórmula copiada, e
`monthsUntil` é subtração sem piso. Um `minDate` deixaria a fórmula intacta e o defeito
sobreviveria a qualquer novo chamador.

**Mudança** — `Recurring.cycleNumberIn(month)` passa a ser o dono do ordinal: 1 no mês de
origem, `null` antes dele — a ausência, e não o zero, porque não há nada ali para numerar.
`ConfirmRecurringUseCase` e `SkipRecurringUseCase` param de contar e passam a perguntar,
recusando com `requireNotNull` antes de escrever qualquer coisa. Junto com
`Recurring.originMonth` e `generatesCycleIn`, a âncora `createdAt` deixa de ser lida à mão
nos quatro pontos que a liam.

A recusa fica dentro do `catch` dos dois, de modo que chega como `Either.Left` e não como
exceção solta — no caso da confirmação isso exigiu mover o cálculo para dentro do bloco,
onde ele estava fora.

**Prova** — `RecurringCycleTest` em `core/model` fixa a numeração (origem é 1, os meses
seguintes contam a partir dali, um mês anterior não tem ciclo); `RecurringCycleFloorTest`
mostra que confirmar e ignorar um mês anterior à origem são recusados sem escrever, com o
par que impede o piso de exagerar — o próprio mês de origem passa. O primeiro teste de
confirmação nasceu **verde pelo motivo errado**: o template do fixture não nomeia conta, e
a confirmação recusava por isso e não pelo piso; o template ganhou uma conta e o teste
passou a morder. Suíte: `./gradlew jvmTest` verde, 1479 testes em 247 classes, e
`:app:android:assembleDebug` verde.

**O que continua fora** — `TransactionRecurring.label` segue concatenando o número cru.
Não precisa mais de defesa: nenhum número abaixo de 1 chega a ser gravado. Os rótulos já
gravados por versões anteriores permanecem como estão; nenhuma migração os reescreve.
